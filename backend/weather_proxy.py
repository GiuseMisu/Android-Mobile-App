import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

import joblib
import pandas as pd
import requests
from flask import Flask, jsonify, request
from sklearn.ensemble import HistGradientBoostingRegressor

app = Flask(__name__)

OPENWEATHER_URL = "https://api.openweathermap.org/data/2.5/forecast"
REQUEST_TIMEOUT_SECONDS = 10

OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
LLM_MODEL = os.environ.get("LLM_MODEL", "openai/gpt-oss-120b:free")
MAX_REVIEWS = 60
MAX_REVIEW_CHARS = 1000

REVIEW_SUMMARY_SYSTEM_PROMPT = (
    "You are an assistant specialized in analyzing hiking trail reviews."
    " Your task is to read a set of reviews and produce a brief, informative summary."
    " The summary must:"
    " - describe the overall opinion of hikers (prevailing sentiment),"
    " - collect the most frequent observations (positive and negative),"
    " - note any recurring details (e.g. trail difficulty, conditions, views, crowding,"
    " parking, signage, safety, practical tips), and be very concise (maximum 3 sentences)."
    " Do not add personal comments beyond the required summary."
    " Respond exclusively with the summary in English."
)

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = Path(os.environ.get("MODEL_PATH", BASE_DIR / "models" / "duration_model.joblib"))
FEATURES_PATH = Path(os.environ.get("FEATURES_PATH", BASE_DIR / "models" / "feature_columns.json"))

SUPPORTED_SURFACES = {"forest", "mountain", "mixed", "road"}
REQUIRED_USER_FIELDS = {"xp_points", "eta", "past_hikes", "avg_speed"}
REQUIRED_HIKE_FIELDS = {"lunghezza", "elevation_gain", "surface_type", "difficulty"}


class ModelState:
    def __init__(self) -> None:
        self.model: HistGradientBoostingRegressor | None = None
        self.feature_columns: list[str] = []
        self.load_error: str | None = None


MODEL_STATE = ModelState()


def _load_model() -> None:
    try:
        model = joblib.load(MODEL_PATH)
        features = json.loads(FEATURES_PATH.read_text())
        if not isinstance(features, list) or not features:
            raise ValueError("feature_columns.json is empty or invalid")
        MODEL_STATE.model = model
        MODEL_STATE.feature_columns = [str(value) for value in features]
        MODEL_STATE.load_error = None
    except Exception as exc:  # noqa: BLE001 - report load failures cleanly
        MODEL_STATE.model = None
        MODEL_STATE.feature_columns = []
        MODEL_STATE.load_error = f"Failed to load model artifacts: {exc}"


def _extract_number(payload: dict, field: str, *, min_value: float | None = None) -> float:
    if field not in payload:
        raise ValueError(f"Missing field: {field}")
    try:
        value = float(payload[field])
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Invalid {field}: must be a number") from exc
    if min_value is not None and value < min_value:
        raise ValueError(f"Invalid {field}: must be >= {min_value}")
    return value


def _validate_surface(surface: Any) -> str:
    if not isinstance(surface, str):
        raise ValueError("Invalid surface_type: must be a string")
    normalized = surface.strip().lower()
    if normalized not in SUPPORTED_SURFACES:
        raise ValueError(
            "Invalid surface_type: must be one of forest, mountain, mixed, road"
        )
    return normalized


def _build_feature_row(payload: dict) -> pd.DataFrame:
    if not isinstance(payload, dict):
        raise ValueError("Payload must be a JSON object")

    user = payload.get("user")
    hike = payload.get("hike")
    if not isinstance(user, dict) or not isinstance(hike, dict):
        raise ValueError("Payload must contain 'user' and 'hike' objects")

    missing_user = REQUIRED_USER_FIELDS - user.keys()
    missing_hike = REQUIRED_HIKE_FIELDS - hike.keys()
    if missing_user:
        raise ValueError(f"Missing user fields: {', '.join(sorted(missing_user))}")
    if missing_hike:
        raise ValueError(f"Missing hike fields: {', '.join(sorted(missing_hike))}")

    surface_type = _validate_surface(hike.get("surface_type"))

    values = {
        "xp_points": _extract_number(user, "xp_points", min_value=0),
        "eta": _extract_number(user, "eta", min_value=0),
        "past_hikes": _extract_number(user, "past_hikes", min_value=0),
        "avg_speed": _extract_number(user, "avg_speed", min_value=0),
        "lunghezza": _extract_number(hike, "lunghezza", min_value=0),
        "elevation_gain": _extract_number(hike, "elevation_gain", min_value=0),
        "difficulty": _extract_number(hike, "difficulty", min_value=1),
    }

    if values["difficulty"] > 5:
        raise ValueError("Invalid difficulty: must be between 1 and 5")

    features = {column: 0.0 for column in MODEL_STATE.feature_columns}
    for key, value in values.items():
        features[key] = float(value)

    surface_column = f"surface_type_{surface_type}"
    if surface_column in features:
        features[surface_column] = 1.0

    feature_frame = pd.DataFrame([features], columns=MODEL_STATE.feature_columns)
    return feature_frame


def _parse_required_float(param_name: str) -> float:
    raw_value = request.args.get(param_name)
    if raw_value is None:
        raise ValueError(f"Missing required query parameter: {param_name}")
    try:
        return float(raw_value)
    except ValueError as exc:
        raise ValueError(f"Invalid {param_name}: must be a number") from exc


def _extract_point(block: dict) -> dict:
    main = block.get("main") or {}
    temp = main.get("temp")
    if temp is None:
        raise ValueError("Missing temperature in OpenWeatherMap response")
    weather_items = block.get("weather") or []
    first_weather = weather_items[0] if weather_items else {}
    return {
        "temperature_c": float(temp),
        "description": str(first_weather.get("description", "")).strip(),
        "icon_id": str(first_weather.get("icon", "")).strip(),
    }


def _closest_forecast_point(forecasts: list, target_epoch_sec: int) -> dict:
    if not forecasts:
        raise ValueError("OpenWeatherMap forecast list is empty")

    valid_items = [item for item in forecasts if isinstance(item.get("dt"), int)]
    if not valid_items:
        raise ValueError("OpenWeatherMap forecast list has no valid timestamps")

    return min(
        valid_items,
        key=lambda item: (abs(item["dt"] - target_epoch_sec), item["dt"] < target_epoch_sec),
    )


@app.get("/health")
def health() -> tuple:
    if MODEL_STATE.model is None:
        return (
            jsonify(
                {
                    "status": "degraded",
                    "model_status": "error",
                    "model_detail": MODEL_STATE.load_error,
                }
            ),
            200,
        )
    return jsonify({"status": "ok", "model_status": "ready"}), 200


@app.get("/weather")
def weather() -> tuple:
    try:
        latitude = _parse_required_float("lat")
        longitude = _parse_required_float("lon")
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400

    api_key = os.environ.get("OPENWEATHER_API_KEY")
    if not api_key:
        return jsonify({"error": "Server is missing OPENWEATHER_API_KEY"}), 500

    params = {
        "lat": latitude,
        "lon": longitude,
        "appid": api_key,
        "units": "metric",
    }

    try:
        response = requests.get(
            OPENWEATHER_URL,
            params=params,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        payload = response.json()
    except requests.HTTPError as exc:
        status_code = exc.response.status_code if exc.response is not None else 502
        return jsonify({"error": "OpenWeatherMap request failed"}), status_code
    except requests.RequestException:
        return jsonify({"error": "Unable to reach OpenWeatherMap"}), 502
    except ValueError:
        return jsonify({"error": "Invalid JSON from OpenWeatherMap"}), 502

    forecast_list = payload.get("list")
    if not isinstance(forecast_list, list) or not forecast_list:
        return jsonify({"error": "OpenWeatherMap response missing required fields"}), 502

    try:
        now_epoch_sec = int(datetime.now(timezone.utc).timestamp())
        current_forecast = _closest_forecast_point(forecast_list, now_epoch_sec)
        plus_1h_forecast = _closest_forecast_point(forecast_list, now_epoch_sec + 3600)
        plus_2h_forecast = _closest_forecast_point(forecast_list, now_epoch_sec + 7200)

        result = {
            "latitude": latitude,
            "longitude": longitude,
            "fetched_at": now_epoch_sec,
            "current": _extract_point(current_forecast),
            "plus_1h": _extract_point(plus_1h_forecast),
            "plus_2h": _extract_point(plus_2h_forecast),
        }
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 502

    return jsonify(result), 200


@app.post("/predict")
def predict() -> tuple:
    if MODEL_STATE.model is None:
        return jsonify({"error": MODEL_STATE.load_error}), 500

    payload = request.get_json(silent=True)
    try:
        features = _build_feature_row(payload)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400

    prediction_log = float(MODEL_STATE.model.predict(features)[0])
    # model trained on log1p(minutes); invert it
    prediction = float(np.expm1(prediction_log))
    prediction = round(max(prediction, 0.0), 1)

    return (
        jsonify({"duration_min": prediction, "unit": "minutes"}),
        200,
    )


@app.post("/summarize-reviews")
def summarize_reviews() -> tuple:
    api_key = os.environ.get("OPENROUTER_API_KEY")
    if not api_key:
        return jsonify({"error": "Server is missing OPENROUTER_API_KEY"}), 500

    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return jsonify({"error": "Body must be a JSON object"}), 400

    raw_reviews = payload.get("reviews")
    if not isinstance(raw_reviews, list):
        return jsonify({"error": "Body must contain a 'reviews' array of strings"}), 400

    reviews: list[str] = []
    for item in raw_reviews:
        if isinstance(item, str):
            text = item.strip()
            if text:
                reviews.append(text[:MAX_REVIEW_CHARS])
    reviews = reviews[:MAX_REVIEWS]

    if not reviews:
        return jsonify({"error": "No usable reviews provided"}), 400

    reviews_block = "\n---\n".join(reviews)
    user_message = f"Reviews:\n\n{reviews_block}"

    try:
        # import here so a missing package only breaks this endpoint, not app startup
        from openai import OpenAI

        client = OpenAI(base_url=OPENROUTER_BASE_URL, api_key=api_key)
        response = client.chat.completions.create(
            model=LLM_MODEL,
            messages=[
                {"role": "system", "content": REVIEW_SUMMARY_SYSTEM_PROMPT},
                {"role": "user", "content": user_message},
            ],
            temperature=0.3,
            max_tokens=300,
            timeout=REQUEST_TIMEOUT_SECONDS * 3,
        )
        summary = (response.choices[0].message.content or "").strip()
    except Exception as exc:  # noqa: BLE001
        app.logger.exception("LLM summarization failed")
        return (
            jsonify(
                {
                    "error": "Summarization failed",
                    "detail": f"{type(exc).__name__}: {str(exc)[:200]}",
                }
            ),
            502,
        )

    if not summary:
        return jsonify({"error": "Empty summary from model"}), 502

    return jsonify({"summary": summary, "review_count": len(reviews)}), 200


_load_model()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)

