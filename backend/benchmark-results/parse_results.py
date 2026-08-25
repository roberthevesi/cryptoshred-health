#!/usr/bin/env python3
"""
CryptoShred Health — JMH Benchmark Results Parser & LaTeX/Chart Generator
Generates academic LaTeX tables and Matplotlib graphs for Dissertation Chapter 5.
"""

import json
import os
import sys

def format_latex_table(title, headers, rows):
    col_spec = "l" + "r" * (len(headers) - 1)
    latex = []
    latex.append(f"% Table: {title}")
    latex.append("\\begin{table}[htbp]")
    latex.append("  \\centering")
    latex.append("  \\small")
    latex.append(f"  \\caption{{{title}}}")
    latex.append(f"  \\label{{tab:{title.lower().replace(' ', '_')}}}")
    latex.append(f"  \\begin{{tabular}}{{{col_spec}}}")
    latex.append("    \\toprule")
    latex.append("    " + " & ".join(headers) + " \\\\")
    latex.append("    \\midrule")
    for row in rows:
        latex.append("    " + " & ".join(str(cell) for cell in row) + " \\\\")
    latex.append("    \\bottomrule")
    latex.append("  \\end{tabular}")
    latex.append("\\end{table}\n")
    return "\n".join(latex)

def safe_float(val, default=0.0):
    try:
        if val is None or val == "NaN" or str(val).strip() == "":
            return default
        return float(val)
    except (ValueError, TypeError):
        return default

def main():
    results_path = sys.argv[1] if len(sys.argv) > 1 else "results.json"
    if not os.path.exists(results_path):
        print(f"Error: {results_path} not found.")
        return

    with open(results_path, "r") as f:
        data = json.load(f)

    # Export CSV
    csv_path = os.path.splitext(results_path)[0] + ".csv"
    with open(csv_path, "w") as f_csv:
        f_csv.write("Benchmark,Mode,Params,Score,ScoreError,Unit\n")
        for item in data:
            name = item.get("benchmark", "")
            mode = item.get("mode", "")
            params = str(item.get("params", {})).replace(",", ";")
            score = safe_float(item.get("primaryMetric", {}).get("score", 0.0))
            err = safe_float(item.get("primaryMetric", {}).get("scoreError", 0.0))
            unit = item.get("primaryMetric", {}).get("scoreUnit", "")
            f_csv.write(f'"{name}","{mode}","{params}",{score:.4f},{err:.4f},"{unit}"\n')

    print("\n" + "="*85)
    print("  🏆 JMH BENCHMARK SUMMARY RESULTS")
    print("="*85)

    enc_rows = []
    del_rows = []
    merkle_rows = []

    for item in data:
        benchmark_name = item.get("benchmark", "").split(".")[-1]
        mode = item.get("mode", "")
        params = item.get("params", {})
        primary_metric = item.get("primaryMetric", {})
        score = safe_float(primary_metric.get("score", 0.0))
        score_error = safe_float(primary_metric.get("scoreError", 0.0))
        unit = primary_metric.get("scoreUnit", "")

        p90 = safe_float(primary_metric.get("scorePercentiles", {}).get("90.0", score))
        p99 = safe_float(primary_metric.get("scorePercentiles", {}).get("99.0", score))

        param_str = f" ({params})" if params else ""
        print(f"  • {benchmark_name:<38}{mode:<6}{param_str:<24}: {score:>12.3f} ± {score_error:<8.3f} {unit}")

        # Only use Average Time (avgt) rows for latency tables
        if mode == "avgt":
            if "EnvelopeEncryption" in item.get("benchmark", ""):
                enc_rows.append([
                    benchmark_name,
                    f"{score:.3f} \\pm {score_error:.3f}",
                    f"{p90:.3f}",
                    f"{p99:.3f}",
                    unit
                ])
            elif "CryptoShredVsPhysicalDelete" in item.get("benchmark", ""):
                n_val = params.get("recordCount", "N/A")
                del_rows.append([
                    benchmark_name,
                    n_val,
                    f"{score:.3f}",
                    f"{score_error:.3f}",
                    unit
                ])
            elif "MerkleTree" in item.get("benchmark", ""):
                leaves = params.get("leafCount", "N/A")
                merkle_rows.append([
                    benchmark_name,
                    leaves,
                    f"{score:.3f}",
                    f"{score_error:.3f}",
                    unit
                ])

    print("\n" + "="*85)
    print("  📑 LATEX TABLES (FOR DISSERTATION CHAPTER 5)")
    print("="*85 + "\n")

    if enc_rows:
        headers = ["Scheme / Operation", "Mean Latency ($\\mu$s)", "p90 ($\\mu$s)", "p99 ($\\mu$s)", "Unit"]
        print(format_latex_table("Cryptographic Overhead Comparison (Plaintext vs AES-GCM vs Vault Envelope)", headers, enc_rows))

    if del_rows:
        headers = ["Deletion Strategy", "Records ($N$)", "Latency ($\\mu$s)", "Error ($\\pm$)", "Unit"]
        print(format_latex_table("GDPR Deletion Complexity: O(1) Crypto-Shredding vs O(N) Physical Deletions", headers, del_rows))

    if merkle_rows:
        headers = ["Merkle Operation", "Leaf Count ($N$)", "Latency ($\\mu$s)", "Error ($\\pm$)", "Unit"]
        print(format_latex_table("Merkle DAG Inclusion Proof and Root Scaling", headers, merkle_rows))

    # Generate standalone Matplotlib chart script
    chart_script_path = os.path.join(os.path.dirname(results_path), "generate_charts.py")
    with open(chart_script_path, "w") as f:
        f.write('''#!/usr/bin/env python3
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
    plt.ylabel("Execution Time (\\u03bcs)")
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
    plt.ylabel("Execution Time (\\u03bcs)")
    plt.xscale("log")
    plt.grid(True, linestyle="--", alpha=0.6)
    plt.legend()
    plt.tight_layout()
    plt.savefig(os.path.join(os.path.dirname(results_file), "merkle_scaling_curve.png"))
    print("  Saved chart: merkle_scaling_curve.png")
''')
    os.chmod(chart_script_path, 0o755)

if __name__ == "__main__":
    main()
