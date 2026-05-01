#!/usr/bin/env python3
"""Train a surrogate model that predicts 7-role genome from scenario features."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

TARGET_COLS = [
    "best_rt",
    "best_cardio",
    "best_radio",
    "best_mh",
    "best_physician",
    "best_pa",
    "best_nurse",
]

FEATURE_COLS = [
    "base_arrival_rate_per_hour",
    "queue_threshold",
    "wait_threshold_hours",
    "p_respiratory",
    "p_cardio",
    "p_trauma",
    "p_psych",
    "p_generic",
    "arrival_profile_type",
    "daily_budget_cap",
]
FEATURE_COLS += [f"arr_share_h{i:02d}" for i in range(24)]
for i in range(1, 7):
    FEATURE_COLS += [
        f"evt{i}_present",
        f"evt{i}_start_hour",
        f"evt{i}_duration",
        f"evt{i}_arrival_multiplier",
        f"evt{i}_mult_respiratory",
        f"evt{i}_mult_cardio",
        f"evt{i}_mult_trauma",
        f"evt{i}_mult_psych",
        f"evt{i}_mult_generic",
    ]

# Bounds from data/synthetic/v2/ga_config.edn
GENOME_BOUNDS = {
    "best_rt": (0, 18),
    "best_cardio": (0, 18),
    "best_radio": (0, 18),
    "best_mh": (0, 18),
    "best_physician": (1, 35),
    "best_pa": (0, 28),
    "best_nurse": (2, 55),
}


def postprocess_predictions(pred_matrix: np.ndarray) -> np.ndarray:
    """Round and clip predictions to integer genome bounds."""
    out = np.rint(pred_matrix).astype(int)
    for j, col in enumerate(TARGET_COLS):
        lo, hi = GENOME_BOUNDS[col]
        out[:, j] = np.clip(out[:, j], lo, hi)
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input-csv",
        default="results/model_training_dataset_all.csv",
        help="Path to merged training dataset CSV.",
    )
    parser.add_argument(
        "--model-out",
        default="results/models/genome_predictor.joblib",
        help="Output path for trained model artifact.",
    )
    parser.add_argument(
        "--metrics-out",
        default="results/models/genome_predictor_metrics.json",
        help="Output path for metrics JSON.",
    )
    parser.add_argument("--random-seed", type=int, default=42)
    parser.add_argument("--test-size", type=float, default=0.2)
    args = parser.parse_args()

    df = pd.read_csv(args.input_csv)

    missing = [c for c in FEATURE_COLS + TARGET_COLS if c not in df.columns]
    if missing:
        raise ValueError(f"Missing required columns: {missing}")

    X = df[FEATURE_COLS].copy()
    y = df[TARGET_COLS].copy()

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.random_seed
    )

    categorical = ["arrival_profile_type"]
    numeric = [c for c in FEATURE_COLS if c not in categorical]
    pre = ColumnTransformer(
        transformers=[
            ("cat", OneHotEncoder(handle_unknown="ignore"), categorical),
            ("num", "passthrough", numeric),
        ]
    )

    model = RandomForestRegressor(
        n_estimators=500,
        random_state=args.random_seed,
        n_jobs=-1,
    )

    pipe = Pipeline([("preprocess", pre), ("model", model)])
    pipe.fit(X_train, y_train)

    y_pred_raw = pipe.predict(X_test)
    y_pred = postprocess_predictions(y_pred_raw)
    y_true = y_test.to_numpy()

    # Metrics
    mae_per_role = {
        col: float(mean_absolute_error(y_true[:, i], y_pred[:, i]))
        for i, col in enumerate(TARGET_COLS)
    }
    exact_match_rate = float(np.mean(np.all(y_pred == y_true, axis=1)))
    per_role_accuracy = {
        col: float(np.mean(y_pred[:, i] == y_true[:, i]))
        for i, col in enumerate(TARGET_COLS)
    }

    model_out = Path(args.model_out)
    metrics_out = Path(args.metrics_out)
    model_out.parent.mkdir(parents=True, exist_ok=True)
    metrics_out.parent.mkdir(parents=True, exist_ok=True)

    joblib.dump(
        {
            "pipeline": pipe,
            "feature_cols": FEATURE_COLS,
            "target_cols": TARGET_COLS,
            "genome_bounds": GENOME_BOUNDS,
        },
        model_out,
    )

    metrics = {
        "n_rows": int(len(df)),
        "n_train": int(len(X_train)),
        "n_test": int(len(X_test)),
        "test_size": args.test_size,
        "mae_per_role": mae_per_role,
        "mean_mae": float(np.mean(list(mae_per_role.values()))),
        "exact_match_rate": exact_match_rate,
        "per_role_accuracy": per_role_accuracy,
    }
    metrics_out.write_text(json.dumps(metrics, indent=2))

    print("Model saved to:", model_out)
    print("Metrics saved to:", metrics_out)
    print("Mean MAE:", f"{metrics['mean_mae']:.4f}")
    print("Exact match rate:", f"{metrics['exact_match_rate']:.4f}")


if __name__ == "__main__":
    main()
