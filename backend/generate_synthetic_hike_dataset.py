from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd

SURFACE_TYPES = ["forest", "mountain", "mixed", "road"]

SURFACE_TIME_MULTIPLIER = {
    "road": 1.0,
    "forest": 1.08,
    "mixed": 1.12,
    "mountain": 1.18,
}

DIFFICULTY_LABELS = {1: "easy", 2: "moderate", 3: "challenging", 4: "hard", 5: "expert"}

GRADE_FT_PER_MILE_BY_DIFFICULTY = {
    1: (0, 60),
    2: (40, 150),
    3: (120, 300),
    4: (250, 500),
    5: (450, 900),
}

MAX_ELEVATION_GAIN_FT = 7000.0
MIN_DURATION_MIN = 0.25


def make_length_bins(length_min: float, length_max: float, n_bins: int) -> list[tuple[float, float]]:
    edges = np.geomspace(length_min, length_max, n_bins + 1)
    return list(zip(edges[:-1], edges[1:]))


def generate_cell(
    n: int,
    length_lo: float,
    length_hi: float,
    difficulty: int,
    surface: str,
    rng: np.random.Generator,
) -> pd.DataFrame:
    length = rng.uniform(length_lo, length_hi, n)

    grade_lo, grade_hi = GRADE_FT_PER_MILE_BY_DIFFICULTY[difficulty]
    grade = rng.uniform(grade_lo, grade_hi, n)
    elevation_gain = length * grade * rng.normal(1.0, 0.08, n)
    elevation_gain = np.clip(elevation_gain, 0, MAX_ELEVATION_GAIN_FT)

    ages = rng.integers(16, 76, n)
    xp_points = rng.gamma(shape=2.2, scale=1200.0, size=n)
    xp_points = np.clip(xp_points, 0, 10000).round().astype(int)
    past_hikes = xp_points / 60 + rng.normal(0, 10, n) + (ages - 25) / 12
    past_hikes = np.clip(past_hikes, 0, 500).round().astype(int)
    avg_speed = 4.2 + 0.00018 * xp_points - 0.015 * np.maximum(0, ages - 45)
    avg_speed += rng.normal(0, 0.4, n)
    avg_speed = np.clip(avg_speed, 2.0, 7.0)

    surface_factor = SURFACE_TIME_MULTIPLIER[surface]
    difficulty_factor = 1.0 + 0.06 * (difficulty - 1)
    elevation_factor = 1.0 + (elevation_gain / 1000.0) * 0.22
    fatigue_factor = 1.0 + np.maximum(0, length - 12.0) / 28.0 * 0.25
    age_factor = 1.0 + np.maximum(0, ages - 50) / 100.0

    base_hours = length / np.maximum(avg_speed, 2.0)
    duration_hours = (
        base_hours * surface_factor * difficulty_factor * elevation_factor * fatigue_factor * age_factor
    )
    duration_hours *= rng.normal(1.0, 0.08, n)
    duration_min = np.clip(duration_hours * 60.0, MIN_DURATION_MIN, None)

    return pd.DataFrame(
        {
            "xp_points": xp_points,
            "eta": ages,
            "past_hikes": past_hikes,
            "avg_speed": np.round(avg_speed, 2),
            "lunghezza": np.round(length, 3),
            "elevation_gain": np.round(elevation_gain, 1),
            "surface_type": surface,
            "difficulty": difficulty,
            "difficulty_label": DIFFICULTY_LABELS[difficulty],
            "duration_min": np.round(duration_min, 2),
        }
    )


def generate_dataset(
    rows_per_cell: int,
    length_min: float,
    length_max: float,
    length_bins: int,
    seed: int,
) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    bins = make_length_bins(length_min, length_max, length_bins)

    frames = []
    for length_lo, length_hi in bins:
        for difficulty in range(1, 6):
            for surface in SURFACE_TYPES:
                frames.append(generate_cell(rows_per_cell, length_lo, length_hi, difficulty, surface, rng))

    dataset = pd.concat(frames, ignore_index=True)
    dataset = dataset.sample(frac=1.0, random_state=seed).reset_index(drop=True)
    return dataset


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a length/difficulty-balanced synthetic hike dataset.")
    parser.add_argument("--rows-per-cell", type=int, default=500,
                         help="Rows generated per (length-bin x difficulty x surface) cell.")
    parser.add_argument("--length-min", type=float, default=0.05,
                         help="Shortest hike length in miles (~0.05mi is roughly a 1-2 min walk).")
    parser.add_argument("--length-max", type=float, default=40.0, help="Longest hike length in miles.")
    parser.add_argument("--length-bins", type=int, default=16, help="Number of log-spaced length bins.")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for reproducibility.")
    parser.add_argument("--output", type=str, default="data/synthetic_hike_dataset.csv", help="Output CSV path.")
    args = parser.parse_args()

    dataset = generate_dataset(
        args.rows_per_cell,
        args.length_min,
        args.length_max,
        args.length_bins,
        args.seed,
    )

    n_cells = args.length_bins * 5 * len(SURFACE_TYPES)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    dataset.to_csv(output_path, index=False)
    print(f"Wrote {len(dataset):,} rows ({n_cells} cells x {args.rows_per_cell} rows/cell) to {output_path}")
    print(f"Length range: {args.length_min}mi - {args.length_max}mi across {args.length_bins} log-spaced bins")
    print(f"Duration range in output: {dataset['duration_min'].min():.1f} - {dataset['duration_min'].max():.1f} min")


if __name__ == "__main__":
    main()
