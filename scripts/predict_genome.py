#!/usr/bin/env python3
"""Predict best staffing genome from scenario-feature JSON."""

from __future__ import annotations

import argparse
import json

import joblib
import numpy as np
import pandas as pd


def postprocess(row: np.ndarray, target_cols: list[str], bounds: dict) -> dict:
    rounded = np.rint(row).astype(int)
    out = {}
    for i, col in enumerate(target_cols):
        lo, hi = bounds[col]
        out[col.replace("best_", "")] = int(np.clip(rounded[i], lo, hi))
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model",
        default="results/models/genome_predictor.joblib",
        help="Path to trained model artifact.",
    )
    parser.add_argument(
        "--features-json",
        required=True,
        help="Path to JSON file containing one scenario feature object.",
    )
    args = parser.parse_args()

    artifact = joblib.load(args.model)
    pipe = artifact["pipeline"]
    feature_cols = artifact["feature_cols"]
    target_cols = artifact["target_cols"]
    bounds = artifact["genome_bounds"]

    features = json.loads(open(args.features_json).read())
    missing = [c for c in feature_cols if c not in features]
    if missing:
        raise ValueError(f"Missing required feature keys: {missing}")

    X = pd.DataFrame([{c: features[c] for c in feature_cols}])
    pred_raw = pipe.predict(X)[0]
    pred = postprocess(pred_raw, target_cols, bounds)
    print(json.dumps(pred, indent=2))


if __name__ == "__main__":
    main()
