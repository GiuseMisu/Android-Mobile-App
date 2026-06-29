from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
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


def prepare_features(data: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    features = data[FEATURE_COLUMNS].copy()
    features = pd.get_dummies(features, columns=["surface_type"], drop_first=False)
    target = data[TARGET_COLUMN]
    return features, target


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

    x_train, x_test, y_train, y_test = train_test_split(
        features,
        target,
        test_size=0.2,
        random_state=seed,
    )

    model = HistGradientBoostingRegressor(
        max_iter=450,
        max_depth=5,
        learning_rate=0.05,
        random_state=seed,
    )

    model.fit(x_train, y_train)

    predictions = model.predict(x_test)

    mse = mean_squared_error(y_test, predictions)
    metrics = {
        "mae": float(mean_absolute_error(y_test, predictions)),
        "rmse": float(np.sqrt(mse)),
        "r2": float(r2_score(y_test, predictions)),
        "rows": int(len(data)),
        "features": list(features.columns),
    }

    model_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, model_path)

    feature_path.parent.mkdir(parents=True, exist_ok=True)
    feature_path.write_text(json.dumps(list(features.columns), indent=2))
    metrics_path.write_text(json.dumps(metrics, indent=2))

    print(f"Saved model to {model_path}")
    print(f"Saved feature columns to {feature_path}")
    print(f"Saved metrics to {metrics_path}")
    print(f"MAE: {metrics['mae']:.2f} min | RMSE: {metrics['rmse']:.2f} | R2: {metrics['r2']:.3f}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train model for hike duration.")
    parser.add_argument(
        "--data",
        type=str,
        default="data/synthetic_hike_dataset.csv",
        help="Path to the input CSV.",
    )
    parser.add_argument(
        "--model-out",
        type=str,
        default="models/duration_model.joblib",
        help="Path to save the trained model.",
    )
    parser.add_argument(
        "--features-out",
        type=str,
        default="models/feature_columns.json",
        help="Path to save the feature column list.",
    )
    parser.add_argument(
        "--metrics-out",
        type=str,
        default="models/metrics.json",
        help="Path to save evaluation metrics.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed.",
    )
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
