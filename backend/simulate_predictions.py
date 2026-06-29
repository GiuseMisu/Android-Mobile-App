from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent
SUPPORTED_SURFACES = ["forest", "mountain", "mixed", "road"]


def load_model(model_path: Path, features_path: Path):
    model = joblib.load(model_path)
    feature_columns = json.loads(features_path.read_text())
    return model, feature_columns


def build_feature_row(user: dict, hike: dict, feature_columns: list[str]) -> pd.DataFrame:
    surface_type = str(hike["surface_type"]).strip().lower()
    if surface_type not in SUPPORTED_SURFACES:
        raise ValueError(f"surface_type must be one of {SUPPORTED_SURFACES}")

    values = {
        "xp_points": float(user["xp_points"]),
        "eta": float(user["eta"]),
        "past_hikes": float(user["past_hikes"]),
        "avg_speed": float(user["avg_speed"]),
        "lunghezza": float(hike["lunghezza"]),
        "elevation_gain": float(hike["elevation_gain"]),
        "difficulty": float(hike["difficulty"]),
    }

    features = {column: 0.0 for column in feature_columns}
    for key, value in values.items():
        if key in features:
            features[key] = value

    surface_column = f"surface_type_{surface_type}"
    if surface_column in features:
        features[surface_column] = 1.0

    return pd.DataFrame([features], columns=feature_columns)


def predict_duration(model, feature_columns, user: dict, hike: dict) -> float:
    row = build_feature_row(user, hike, feature_columns)
    prediction_log = float(model.predict(row)[0])
    prediction = float(np.expm1(prediction_log))
    return round(max(prediction, 0.0), 2)


def training_density(train_df: pd.DataFrame | None, length: float, window: float = 1.0) -> int | None:
    if train_df is None:
        return None
    nearby = train_df[
        (train_df["lunghezza"] >= length - window) & (train_df["lunghezza"] <= length + window)
    ]
    return int(len(nearby))


SCENARIOS = [
    {
        "label": "Tiny flat path (~0.1mi), easy -- the '2 minute walk' case",
        "user": {"xp_points": 1200, "eta": 35, "past_hikes": 15, "avg_speed": 4.0},
        "hike": {"lunghezza": 0.1, "elevation_gain": 5, "surface_type": "road", "difficulty": 1},
    },
    {
        "label": "Tiny steep scramble (~0.1mi), difficulty 5 -- same hiker as above",
        "user": {"xp_points": 1200, "eta": 35, "past_hikes": 15, "avg_speed": 4.0},
        "hike": {"lunghezza": 0.1, "elevation_gain": 70, "surface_type": "mountain", "difficulty": 5},
    },
    {
        "label": "Very short flat road walk, casual beginner",
        "user": {"xp_points": 150, "eta": 34, "past_hikes": 1, "avg_speed": 3.5},
        "hike": {"lunghezza": 0.6, "elevation_gain": 10, "surface_type": "road", "difficulty": 1},
    },
    {
        "label": "Short steep mountain scramble, experienced hiker",
        "user": {"xp_points": 6000, "eta": 29, "past_hikes": 180, "avg_speed": 5.2},
        "hike": {"lunghezza": 1.2, "elevation_gain": 350, "surface_type": "mountain", "difficulty": 4},
    },
    {
        "label": "Short forest loop, average hiker",
        "user": {"xp_points": 1200, "eta": 40, "past_hikes": 15, "avg_speed": 4.0},
        "hike": {"lunghezza": 2.0, "elevation_gain": 90, "surface_type": "forest", "difficulty": 2},
    },
    {
        "label": "Medium mixed-terrain hike, average hiker (baseline)",
        "user": {"xp_points": 1200, "eta": 40, "past_hikes": 15, "avg_speed": 4.0},
        "hike": {"lunghezza": 8.0, "elevation_gain": 500, "surface_type": "mixed", "difficulty": 3},
    },
    {
        "label": "Long mountain trek, expert hiker",
        "user": {"xp_points": 8500, "eta": 33, "past_hikes": 220, "avg_speed": 5.0},
        "hike": {"lunghezza": 20.0, "elevation_gain": 1400, "surface_type": "mountain", "difficulty": 5},
    },
]


def print_result(label: str, user: dict, hike: dict, prediction: float, density: int | None) -> None:
    pace = prediction / hike["lunghezza"] if hike["lunghezza"] else float("nan")
    print(f"\n{label}")
    print(f"  user: xp={user['xp_points']}, age={user['eta']}, past_hikes={user['past_hikes']}, avg_speed={user['avg_speed']} mph")
    print(f"  hike: {hike['lunghezza']} mi, {hike['elevation_gain']} ft gain, {hike['surface_type']}, difficulty {hike['difficulty']}")
    print(f"  --> predicted duration: {prediction} min  ({pace:.1f} min/mi)")
    if density is not None:
        flag = "  <-- SPARSE training data, low confidence" if density < 100 else ""
        print(f"  training rows within +/-1mi of this length: {density}{flag}")


def run_scenarios(model, feature_columns, train_df) -> None:
    for sc in SCENARIOS:
        prediction = predict_duration(model, feature_columns, sc["user"], sc["hike"])
        density = training_density(train_df, sc["hike"]["lunghezza"])
        print_result(sc["label"], sc["user"], sc["hike"], prediction, density)


def run_custom(model, feature_columns, train_df, args) -> None:
    user = {
        "xp_points": args.xp,
        "eta": args.eta,
        "past_hikes": args.past_hikes,
        "avg_speed": args.avg_speed,
    }
    hike = {
        "lunghezza": args.length,
        "elevation_gain": args.elevation,
        "surface_type": args.surface,
        "difficulty": args.difficulty,
    }
    prediction = predict_duration(model, feature_columns, user, hike)
    density = training_density(train_df, hike["lunghezza"])
    print_result("Custom scenario", user, hike, prediction, density)


def run_interactive(model, feature_columns, train_df) -> None:
    print("Enter user profile:")
    user = {
        "xp_points": float(input("  xp_points: ")),
        "eta": float(input("  age (eta): ")),
        "past_hikes": float(input("  past_hikes: ")),
        "avg_speed": float(input("  avg_speed (mph): ")),
    }
    print("Enter hike:")
    hike = {
        "lunghezza": float(input("  length (miles): ")),
        "elevation_gain": float(input("  elevation_gain (ft): ")),
        "surface_type": input(f"  surface_type {SUPPORTED_SURFACES}: ").strip().lower(),
        "difficulty": float(input("  difficulty (1-5): ")),
    }
    prediction = predict_duration(model, feature_columns, user, hike)
    density = training_density(train_df, hike["lunghezza"])
    print_result("Interactive scenario", user, hike, prediction, density)


def main() -> None:
    parser = argparse.ArgumentParser(description="Simulate hike duration predictions.")
    parser.add_argument("--model-path", type=str, default=str(BASE_DIR / "models" / "duration_model.joblib"))
    parser.add_argument("--features-path", type=str, default=str(BASE_DIR / "models" / "feature_columns.json"))
    parser.add_argument("--data-path", type=str, default=str(BASE_DIR / "data" / "synthetic_hike_dataset.csv"),
                         help="Training CSV, used only to flag sparse-data predictions. Optional.")
    parser.add_argument("--interactive", action="store_true")
    parser.add_argument("--custom", action="store_true")
    parser.add_argument("--xp", type=float, default=1000)
    parser.add_argument("--eta", type=float, default=30)
    parser.add_argument("--past-hikes", type=float, default=10)
    parser.add_argument("--avg-speed", type=float, default=4.0)
    parser.add_argument("--length", type=float, default=5.0)
    parser.add_argument("--elevation", type=float, default=200)
    parser.add_argument("--surface", type=str, default="forest", choices=SUPPORTED_SURFACES)
    parser.add_argument("--difficulty", type=float, default=2)
    args = parser.parse_args()

    model, feature_columns = load_model(Path(args.model_path), Path(args.features_path))

    data_path = Path(args.data_path)
    train_df = pd.read_csv(data_path) if data_path.exists() else None

    if args.interactive:
        run_interactive(model, feature_columns, train_df)
    elif args.custom:
        run_custom(model, feature_columns, train_df, args)
    else:
        run_scenarios(model, feature_columns, train_df)


if __name__ == "__main__":
    main()
