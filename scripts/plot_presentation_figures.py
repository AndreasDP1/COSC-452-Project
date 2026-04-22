#!/usr/bin/env python3
"""
Read results/presentation_ga_runs.csv and presentation_hand_baseline.csv;
write comparison PNGs into results/figures/.

Requires: matplotlib  (python3 -m pip install matplotlib)

Run from repo root:
  python3 scripts/plot_presentation_figures.py
"""
from __future__ import annotations

import csv
import sys
from pathlib import Path


def load_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        print("Missing:", path, file=sys.stderr)
        return []
    rows: list[dict[str, str]] = []
    with path.open(newline="") as f:
        for row in csv.DictReader(f):
            rows.append(row)
    return rows


def ffloat(row: dict[str, str], key: str, default: float = 0.0) -> float:
    v = row.get(key, "") or ""
    try:
        return float(v)
    except ValueError:
        return default


def short_label(s: str, n: int = 22) -> str:
    if len(s) <= n:
        return s
    return s[: n - 1] + "..."


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    ga_path = root / "results" / "presentation_ga_runs.csv"
    hand_path = root / "results" / "presentation_hand_baseline.csv"
    out_dir = root / "results" / "figures"
    out_dir.mkdir(parents=True, exist_ok=True)

    try:
        import matplotlib.pyplot as plt
    except ImportError:
        print("matplotlib not installed. Run: python3 -m pip install matplotlib", file=sys.stderr)
        sys.exit(1)

    rows = load_csv(ga_path)
    if not rows:
        print("No rows in", ga_path, file=sys.stderr)
        sys.exit(1)

    labels = [r["label"] for r in rows]
    x = list(range(len(labels)))
    mean_err = [ffloat(r, "mean_error") for r in rows]

    # --- Mean error: log scale (all runs, incl. ablation) ---
    fig, ax = plt.subplots(figsize=(10, 4))
    pos = [max(m, 1e-12) for m in mean_err]
    ax.bar(x, pos, color="steelblue")
    ax.set_xticks(x)
    ax.set_xticklabels([short_label(l) for l in labels], rotation=35, ha="right", fontsize=8)
    ax.set_ylabel("Mean error (log scale)")
    ax.set_yscale("log")
    ax.set_title("Mean error by run (ablation ~1e4 vs feasible ~0.1)")
    fig.tight_layout()
    fig.savefig(out_dir / "mean_error_log.png", dpi=150)
    plt.close(fig)

    # --- Mean error: linear, no ablation ---
    no_ab = [(i, r) for i, r in enumerate(rows) if "ablation" not in r["label"].lower()]
    if len(no_ab) >= 1:
        xi = list(range(len(no_ab)))
        err = [ffloat(r, "mean_error") for _i, r in no_ab]
        labs = [short_label(r["label"]) for _i, r in no_ab]
        fig, ax = plt.subplots(figsize=(10, 4))
        ax.bar(xi, err, color="#457b9d")
        ax.set_xticks(xi)
        ax.set_xticklabels(labs, rotation=35, ha="right", fontsize=8)
        ax.set_ylabel("Mean error")
        ax.set_title("Mean error (feasible runs only; ablation omitted)")
        fig.tight_layout()
        fig.savefig(out_dir / "mean_error_linear_no_ablation.png", dpi=150)
        plt.close(fig)

    # --- Zoom: feasible only (same as linear subset, tight y-axis) ---
    if len(no_ab) >= 2:
        err2 = [ffloat(r, "mean_error") for _i, r in no_ab]
        labs2 = [short_label(r["label"], 18) for _i, r in no_ab]
        lo, hi = min(err2), max(err2)
        pad = max((hi - lo) * 0.4, 0.001)
        fig, ax = plt.subplots(figsize=(8, 4))
        ax.bar(range(len(err2)), err2, color="#1d3557")
        ax.set_xticks(range(len(err2)))
        ax.set_xticklabels(labs2, rotation=30, ha="right", fontsize=8)
        ax.set_ylabel("Mean error")
        ax.set_ylim(max(0.0, lo - pad), hi + pad)
        ax.set_title("Zoom: feasible mean error (small gaps)")
        fig.tight_layout()
        fig.savefig(out_dir / "mean_error_zoom_feasible.png", dpi=150)
        plt.close(fig)

    hand = load_csv(hand_path)
    if hand:
        scen = [
            h["scenario_id"].replace("week-", "").replace("v1-real-hourly", "v1 real-hr")
            for h in hand
        ]
        waits = [ffloat(h, "avg_wait") for h in hand]
        fit = [ffloat(h, "fitness") for h in hand]
        fig, ax = plt.subplots(figsize=(9, 4))
        ax.bar(range(len(scen)), waits, color="teal")
        ax.set_xticks(range(len(scen)))
        ax.set_xticklabels(scen, rotation=25, ha="right")
        ax.set_ylabel("Avg wait (hand-picked demo staffing)")
        ax.set_title("Same staffing, different scenario stress")
        fig.tight_layout()
        fig.savefig(out_dir / "hand_baseline_wait_by_scenario.png", dpi=150)
        plt.close(fig)

        fig, ax = plt.subplots(figsize=(9, 4))
        ax.bar(range(len(scen)), fit, color="slategray")
        ax.set_xticks(range(len(scen)))
        ax.set_xticklabels(scen, rotation=25, ha="right")
        ax.set_ylabel("Fitness (lower better)")
        ax.set_title("Hand-picked staffing: fitness by scenario")
        fig.tight_layout()
        fig.savefig(out_dir / "hand_baseline_fitness_by_scenario.png", dpi=150)
        plt.close(fig)

    wmax = [ffloat(r, "avg_wait_max") for r in rows]
    wmean = [ffloat(r, "avg_wait_mean") for r in rows]
    qmax = [ffloat(r, "avg_queue_len_max") for r in rows]
    mr = [ffloat(r, "match_rate_first") for r in rows]

    def idx(pred):
        for i, lab in enumerate(labels):
            if pred(lab):
                return i
        return None

    fig, ax = plt.subplots(figsize=(10, 4))
    ax.bar(x, qmax, color="#2a6f97")
    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=35, ha="right")
    ax.set_ylabel("Avg queue length (max over scenarios)")
    ax.set_title("Queue length max (often non-zero when wait is near 0)")
    fig.tight_layout()
    fig.savefig(out_dir / "avg_queue_len_max.png", dpi=150)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(10, 4))
    ax.bar(x, wmax, color="coral")
    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=35, ha="right")
    ax.set_ylabel("Avg wait (max over scenarios in that run)")
    ax.set_title("Max wait (hours) over scenarios in each run")
    fig.tight_layout()
    fig.subplots_adjust(bottom=0.12)
    fig.savefig(out_dir / "avg_wait_max.png", dpi=150)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(10, 4))
    ax.bar(x, wmean, color="orange")
    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=35, ha="right")
    ax.set_ylabel("Avg wait (mean over scenarios)")
    fig.tight_layout()
    fig.savefig(out_dir / "avg_wait_mean.png", dpi=150)
    plt.close(fig)

    fig, ax = plt.subplots(figsize=(10, 4))
    ax.bar(x, mr, color="mediumpurple")
    ax.set_xticks(x)
    ax.set_xticklabels(labels, rotation=35, ha="right")
    ax.set_ylabel("Specialty match rate (1st scenario)")
    ax.set_ylim(0, 1.05)
    ax.set_title("Specialty match rate")
    fig.tight_layout()
    fig.savefig(out_dir / "match_rate_first.png", dpi=150)
    plt.close(fig)

    i_b = idx(lambda s: s.startswith("B-ga-lexicase"))
    i_c = idx(lambda s: s.startswith("C-ga-tournament"))
    if i_b is not None and i_c is not None:
        fig, axes = plt.subplots(1, 2, figsize=(8, 4))
        names = ["Lexicase (B)", "Tournament (C)"]
        axes[0].bar(names, [mean_err[i_b], mean_err[i_c]], color=["#2a9d8f", "#e9c46a"])
        axes[0].set_ylabel("Mean error")
        axes[0].set_title("2-case: mean fitness error")
        axes[1].bar(names, [mr[i_b], mr[i_c]], color=["#2a9d8f", "#e9c46a"])
        axes[1].set_ylabel("Match rate (1st scen)")
        axes[1].set_ylim(0, 1.05)
        axes[1].set_title("2-case: specialty match")
        fig.suptitle("Synthetic + real hourly: lexicase vs tournament")
        fig.tight_layout()
        fig.savefig(out_dir / "lexicase_vs_tournament_2case.png", dpi=150)
        plt.close(fig)

    i_a = idx(lambda s: s == "A-ga-synthetic-only")
    i_d = idx(lambda s: "random-events" in s)
    if i_a is not None and i_d is not None:
        fig, axes = plt.subplots(1, 2, figsize=(8, 4))
        nm = ["A: fixed EDN events", "D: random events"]
        axes[0].bar(nm, [mean_err[i_a], mean_err[i_d]], color=["#457b9d", "#e63946"])
        axes[0].set_ylabel("Mean error")
        axes[0].set_title("Mean error (fitness)")
        axes[1].bar(nm, [mr[i_a], mr[i_d]], color=["#457b9d", "#e63946"])
        axes[1].set_ylabel("Specialty match rate (1st scen)")
        axes[1].set_ylim(0, 1.05)
        axes[1].set_title("Match rate")
        fig.suptitle("Fixed event calendar vs random shock draws")
        fig.tight_layout()
        fig.savefig(out_dir / "synthetic_fixed_vs_random_events.png", dpi=150)
        plt.close(fig)

    fig, axes = plt.subplots(2, 2, figsize=(11, 8))
    sl = [short_label(l, 20) for l in labels]
    axes[0, 0].bar(x, mean_err, color="steelblue")
    axes[0, 0].set_xticks(x)
    axes[0, 0].set_xticklabels(sl, rotation=45, ha="right", fontsize=7)
    axes[0, 0].set_ylabel("Mean error")
    axes[0, 0].set_title("Mean error")

    axes[0, 1].bar(x, wmax, color="coral")
    axes[0, 1].set_xticks(x)
    axes[0, 1].set_xticklabels(sl, rotation=45, ha="right", fontsize=7)
    axes[0, 1].set_ylabel("Wait max")
    axes[0, 1].set_title("Avg wait (max over scenarios)")

    axes[1, 0].bar(x, wmean, color="orange")
    axes[1, 0].set_xticks(x)
    axes[1, 0].set_xticklabels(sl, rotation=45, ha="right", fontsize=7)
    axes[1, 0].set_ylabel("Wait mean")
    axes[1, 0].set_title("Avg wait (mean over scenarios)")

    axes[1, 1].bar(x, mr, color="mediumpurple")
    axes[1, 1].set_xticks(x)
    axes[1, 1].set_xticklabels(sl, rotation=45, ha="right", fontsize=7)
    axes[1, 1].set_ylabel("Match rate")
    axes[1, 1].set_ylim(0, 1.05)
    axes[1, 1].set_title("Specialty match (1st scenario)")

    fig.suptitle("Presentation metrics grid (from presentation_ga_runs.csv)")
    fig.tight_layout()
    fig.savefig(out_dir / "metrics_grid_4panel.png", dpi=150)
    plt.close(fig)

    five_path = root / "results" / "presentation_5case_errors.csv"
    five_rows = load_csv(five_path)
    if five_rows:
        sids = [r["scenario_id"].replace("week-", "") for r in five_rows]
        errs = [ffloat(r, "error_best_genome") for r in five_rows]
        fig, ax = plt.subplots(figsize=(9, 4))
        ax.bar(range(len(sids)), errs, color="#bc6c25")
        ax.set_xticks(range(len(sids)))
        ax.set_xticklabels(sids, rotation=25, ha="right")
        ax.set_ylabel("Error (lower better)")
        ax.set_title("5-case lexicase: error per scenario (best genome)")
        fig.tight_layout()
        fig.savefig(out_dir / "five_case_per_scenario_errors.png", dpi=150)
        plt.close(fig)

    print("Wrote PNGs under", out_dir)


if __name__ == "__main__":
    main()
