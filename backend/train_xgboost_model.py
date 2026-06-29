from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, mean_absolute_percentage_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split

FEATURE_COLUMNS = [
    "xp_points",
    "eta",
    "past_hikes",
    "avg_speed",
    "lunghezza",
    "elevation_gain",
    "surface_type",
    "difficulty",
]
TARGET_COLUMN = "duration_min"

LENGTH_BUCKET_EDGES = np.geomspace(0.05, 40.0, 17)


def prepare_features(data: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    features = data[FEATURE_COLUMNS].copy()
    features = pd.get_dummies(features, columns=["surface_type"], drop_first=False)
    target = data[TARGET_COLUMN]
    return features, target


def make_stratify_key(data: pd.DataFrame) -> pd.Series:
    length_bucket = pd.cut(data["lunghezza"], bins=LENGTH_BUCKET_EDGES, labels=False, include_lowest=True)
    return length_bucket.astype(str) + "_" + data["difficulty"].astype(str)


def evaluate_by_segment(y_true: pd.Series, y_pred: np.ndarray, segment: pd.Series, segment_name: str) -> dict:
    results = {}
    df = pd.DataFrame({"y_true": y_true.values, "y_pred": y_pred, "segment": segment.values})
    for segment_value, group in df.groupby("segment", observed=True):
        mae = mean_absolute_error(group["y_true"], group["y_pred"])
        mape = mean_absolute_percentage_error(group["y_true"], group["y_pred"]) * 100
        results[str(segment_value)] = {
            "n": int(len(group)),
            "mae_min": round(float(mae), 3),
            "mape_pct": round(float(mape), 2),
        }
    return {segment_name: results}


def train_model(
    data_path: Path,
    model_path: Path,
    feature_path: Path,
    metrics_path: Path,
    seed: int,
) -> None:
    data = pd.read_csv(data_path)
    data = data.drop(columns=["difficulty_label"], errors="ignore")

    features, target = prepare_features(data)
    stratify_key = make_stratify_key(data)

    x_train, x_test, y_train, y_test, strat_train, strat_test = train_test_split(
        features,
        target,
        stratify_key,
        test_size=0.2,
        random_state=seed,
        stratify=stratify_key,
    )

    y_train_log = np.log1p(y_train)

    model = HistGradientBoostingRegressor(
        max_iter=600,
        max_depth=6,
        learning_rate=0.05,
        l2_regularization=0.1,
        random_state=seed,
    )

    model.fit(x_train, y_train_log)

    predictions_log = model.predict(x_test)
    predictions = np.expm1(predictions_log)
    predictions = np.clip(predictions, 0.0, None)

    mse = mean_squared_error(y_test, predictions)
    metrics = {
        "mae": float(mean_absolute_error(y_test, predictions)),
        "rmse": float(np.sqrt(mse)),
        "mape_pct": float(mean_absolute_percentage_error(y_test, predictions) * 100),
        "r2": float(r2_score(y_test, predictions)),
        "rows": int(len(data)),
        "features": list(features.columns),
    }

    length_bucket_test = pd.cut(
        x_test["lunghezza"], bins=LENGTH_BUCKET_EDGES, labels=False, include_lowest=True
    ).astype(str)
    metrics.update(evaluate_by_segment(y_test, predictions, length_bucket_test, "by_length_bucket"))

    difficulty_test = x_test["difficulty"].astype(int).astype(str)
    metrics.update(evaluate_by_segment(y_test, predictions, difficulty_test, "by_difficulty"))

    model_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, model_path)

    feature_path.parent.mkdir(parents=True, exist_ok=True)
    feature_path.write_text(json.dumps(list(features.columns), indent=2))
    metrics_path.write_text(json.dumps(metrics, indent=2))

    print(f"Saved model to {model_path}")
    print(f"Saved feature columns to {feature_path}")
    print(f"Saved metrics to {metrics_path}")
    print(f"\nOverall: MAE {metrics['mae']:.2f} min | MAPE {metrics['mape_pct']:.2f}% | RMSE {metrics['rmse']:.2f} | R2 {metrics['r2']:.3f}")

    print("\nMAPE by length bucket (bucket index 0 = shortest, 15 = longest):")
    for bucket, stats in sorted(metrics["by_length_bucket"].items(), key=lambda kv: int(kv[0])):
        print(f"  bucket {bucket:>2}: n={stats['n']:>5}  MAE={stats['mae_min']:>8.2f} min  MAPE={stats['mape_pct']:>6.2f}%")

    print("\nMAPE by difficulty:")
    for difficulty, stats in sorted(metrics["by_difficulty"].items(), key=lambda kv: int(kv[0])):
        print(f"  difficulty {difficulty}: n={stats['n']:>5}  MAE={stats['mae_min']:>8.2f} min  MAPE={stats['mape_pct']:>6.2f}%")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train model for hike duration.")
    parser.add_argument("--data", type=str, default="data/synthetic_hike_dataset.csv", help="Path to the input CSV.")
    parser.add_argument("--model-out", type=str, default="models/duration_model.joblib", help="Path to save the trained model.")
    parser.add_argument("--features-out", type=str, default="models/feature_columns.json", help="Path to save the feature column list.")
    parser.add_argument("--metrics-out", type=str, default="models/metrics.json", help="Path to save evaluation metrics.")
    parser.add_argument("--seed", type=int, default=42, help="Random seed.")
    args = parser.parse_args()

    train_model(
        Path(args.data),
        Path(args.model_out),
        Path(args.features_out),
        Path(args.metrics_out),
        args.seed,
    )


if __name__ == "__main__":
    main()
