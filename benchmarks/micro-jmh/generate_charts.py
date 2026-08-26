#!/usr/bin/env python3
import json, os, sys
try:
    import matplotlib.pyplot as plt
    import numpy as np
except ImportError:
    print("Matplotlib not installed. Run 'pip install matplotlib numpy' to generate visual plots.")
    sys.exit(0)

results_file = sys.argv[1] if len(sys.argv) > 1 else "results.json"
if not os.path.exists(results_file):
    sys.exit(1)

with open(results_file, "r") as f:
    data = json.load(f)

# 1. Plot O(1) vs O(N) Deletion Curve
del_data = {}
for item in data:
    if "CryptoShredVsPhysicalDelete" in item.get("benchmark", ""):
        name = item.get("benchmark", "").split(".")[-1]
        n = int(item.get("params", {}).get("recordCount", 0))
        score = item.get("primaryMetric", {}).get("score", 0.0)
        del_data.setdefault(name, {})[n] = score

if del_data:
    plt.figure(figsize=(9, 5), dpi=300)
    for name, points in del_data.items():
        sorted_n = sorted(points.keys())
        scores = [points[k] for k in sorted_n]
        plt.plot(sorted_n, scores, marker='o', label=name)
    plt.title("GDPR Article 17 Erasure Latency: $O(1)$ Crypto-Shred vs $O(N)$ Physical Delete")
    plt.xlabel("Patient Records ($N$)")
    plt.ylabel("Execution Time (\u03bcs)")
    plt.grid(True, linestyle="--", alpha=0.6)
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(os.path.dirname(results_file), "deletion_complexity_curve.png"))
    print("  Saved chart: deletion_complexity_curve.png")

# 2. Plot Merkle Tree Scaling
merkle_data = {}
for item in data:
    if "MerkleTree" in item.get("benchmark", ""):
        name = item.get("benchmark", "").split(".")[-1]
        n = int(item.get("params", {}).get("leafCount", 0))
        score = item.get("primaryMetric", {}).get("score", 0.0)
        merkle_data.setdefault(name, {})[n] = score

if merkle_data:
    plt.figure(figsize=(9, 5), dpi=300)
    for name, points in merkle_data.items():
        sorted_n = sorted(points.keys())
        scores = [points[k] for k in sorted_n]
        plt.plot(sorted_n, scores, marker='s', label=name)
    plt.title("Merkle DAG Audit Trail Scalability & Verification Speed")
    plt.xlabel("Total Leaf Count ($N$)")
    plt.ylabel("Execution Time (\u03bcs)")
    plt.xscale("log")
    plt.grid(True, linestyle="--", alpha=0.6)
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(os.path.dirname(results_file), "merkle_scaling_curve.png"))
    print("  Saved chart: merkle_scaling_curve.png")
