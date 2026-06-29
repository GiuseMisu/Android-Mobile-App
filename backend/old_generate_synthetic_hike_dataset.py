from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd

SURFACE_TYPES = ["forest", "mountain", "mixed", "road"]
SURFACE_PROBS = [0.4, 0.25, 0.25, 0.1]
SURFACE_SCORES = {
    "road": 0.0,
    "forest": 0.3,
    "mixed": 0.6,
    "mountain": 0.9,
}
SURFACE_GRADE_MULTIPLIER = {
    "road": 0.8,
    "forest": 1.0,
    "mixed": 1.1,
    "mountain": 1.25,
}
SURFACE_TIME_MULTIPLIER = {
    "road": 1.0,
    "forest": 1.08,
    "mixed": 1.12,
    "mountain": 1.18,
}
DEFAULT_DIFFICULTY_BINS = [5, 9, 13, 17]
DEFAULT_DIFFICULTY_QUANTILES = [0.2, 0.4, 0.6, 0.8]
DIFFICULTY_LABELS = {
    1: "easy",
    2: "moderate",
    3: "challenging",
    4: "hard",
    5: "expert",
}


def _normalize_surface_probs(values: list[float]) -> list[float]:
    probs = np.array(values, dtype=float)
    if probs.size != len(SURFACE_TYPES):
        raise ValueError("surface_probs must provide four values")
    if np.any(probs < 0):
        raise ValueError("surface_probs must be non-negative")
    total = probs.sum()
    if not np.isfinite(total) or total <= 0:
        raise ValueError("surface_probs must sum to a positive value")
    return (probs / total).tolist()


def _resolve_difficulty_bins(
    difficulty_score: np.ndarray,
    binning: str,
    fixed_bins: list[float],
    quantiles: list[float],
) -> np.ndarray:
    if binning == "quantile":
        quantile_values = np.array(quantiles, dtype=float)
        if quantile_values.size != 4 or np.any(quantile_values <= 0) or np.any(quantile_values >= 1):
            raise ValueError("difficulty_quantiles must be four values between 0 and 1")
        quantile_values = np.sort(quantile_values)
        return np.quantile(difficulty_score, quantile_values)

    fixed = np.array(fixed_bins, dtype=float)
    if fixed.size != 4 or np.any(~np.isfinite(fixed)):
        raise ValueError("difficulty_bins must be four finite values")
    return np.sort(fixed)


def generate_dataset(
    rows: int,
    seed: int,
    surface_probs: list[float],
    difficulty_binning: str,
    difficulty_bins: list[float],
    difficulty_quantiles: list[float],
) -> pd.DataFrame:
    rng = np.random.default_rng(seed)

    ages = rng.integers(16, 76, rows)

    xp_points = rng.gamma(shape=2.2, scale=1200.0, size=rows)
    xp_points = np.clip(xp_points, 0, 10000).round().astype(int)

    past_hikes = xp_points / 60 + rng.normal(0, 10, rows) + (ages - 25) / 12
    past_hikes = np.clip(past_hikes, 0, 500).round().astype(int)

    avg_speed = 4.2 + 0.00018 * xp_points - 0.015 * np.maximum(0, ages - 45)
    avg_speed += rng.normal(0, 0.4, rows)
    avg_speed = np.clip(avg_speed, 2.0, 7.0)

    length = rng.lognormal(mean=np.log(8.0), sigma=0.55, size=rows)
    length = np.clip(length, 1.0, 40.0)

    surface_types = rng.choice(SURFACE_TYPES, size=rows, p=surface_probs)

    grade_multiplier = np.array([SURFACE_GRADE_MULTIPLIER[surface] for surface in surface_types])
    base_grade = rng.uniform(30, 120, rows) * grade_multiplier
    elevation_gain = length * base_grade + rng.normal(0, 60, rows)
    elevation_gain = np.clip(elevation_gain, 0, 2500)

    surface_score = np.array([SURFACE_SCORES[surface] for surface in surface_types])
    difficulty_score = 0.35 * length + 0.004 * elevation_gain + surface_score
    difficulty_score += rng.normal(0, 0.7, rows)

    bins = _resolve_difficulty_bins(
        difficulty_score,
        difficulty_binning,
        difficulty_bins,
        difficulty_quantiles,
    )
    difficulty_rating = np.digitize(difficulty_score, bins=bins) + 1
    difficulty_rating = np.clip(difficulty_rating, 1, 5)

    difficulty_label = np.array([DIFFICULTY_LABELS[int(rating)] for rating in difficulty_rating])

    surface_factor = np.array([SURFACE_TIME_MULTIPLIER[surface] for surface in surface_types])
    difficulty_factor = 1.0 + 0.06 * (difficulty_rating - 1)
    elevation_factor = 1.0 + (elevation_gain / 1000.0) * 0.22
    fatigue_factor = 1.0 + np.maximum(0, length - 12.0) / 28.0 * 0.25
    age_factor = 1.0 + np.maximum(0, ages - 50) / 100.0

    base_hours = length / np.maximum(avg_speed, 2.0)
    duration_hours = (
        base_hours
        * surface_factor
        * difficulty_factor
        * elevation_factor
        * fatigue_factor
        * age_factor
    )
    duration_hours *= rng.normal(1.0, 0.08, rows)
    duration_min = np.clip(duration_hours * 60.0, 20.0, None)
    duration_min = np.round(duration_min, 1)

    return pd.DataFrame(
        {
            "xp_points": xp_points,
            "eta": ages,
            "past_hikes": past_hikes,
            "avg_speed": np.round(avg_speed, 2),
            "lunghezza": np.round(length, 2),
            "elevation_gain": np.round(elevation_gain, 0).astype(int),
            "surface_type": surface_types,
            "difficulty": difficulty_rating.astype(int),
            "difficulty_label": difficulty_label,
            "duration_min": duration_min,
        }
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate synthetic hike dataset.")
    parser.add_argument(
        "--rows",
        type=int,
        default=30000,
        help="Number of rows to generate.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for reproducibility.",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="data/synthetic_hike_dataset.csv",
        help="Output CSV path.",
    )
    parser.add_argument(
        "--surface-probs",
        type=float,
        nargs=4,
        default=SURFACE_PROBS,
        help="Probabilities for forest, mountain, mixed, road surfaces.",
    )
    parser.add_argument(
        "--difficulty-binning",
        choices=["fixed", "quantile"],
        default="fixed",
        help="Use fixed thresholds or quantile-based bins for difficulty.",
    )
    parser.add_argument(
        "--difficulty-bins",
        type=float,
        nargs=4,
        default=DEFAULT_DIFFICULTY_BINS,
        help="Score thresholds for difficulty ratings 1-5.",
    )
    parser.add_argument(
        "--difficulty-quantiles",
        type=float,
        nargs=4,
        default=DEFAULT_DIFFICULTY_QUANTILES,
        help="Quantiles used when difficulty-binning is 'quantile'.",
    )
    args = parser.parse_args()

    surface_probs = _normalize_surface_probs(args.surface_probs)
    dataset = generate_dataset(
        args.rows,
        args.seed,
        surface_probs,
        args.difficulty_binning,
        args.difficulty_bins,
        args.difficulty_quantiles,
    )
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    dataset.to_csv(output_path, index=False)
    print(f"Wrote {len(dataset):,} rows to {output_path}")


if __name__ == "__main__":
    main()
