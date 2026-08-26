# Tier 1: JMH Algorithmic Microbenchmarks — CryptoShred Health

This module contains the **Java Microbenchmark Harness (JMH)** suite evaluating low-level cryptographic operations, algorithmic deletion complexity, and Merkle tree audit scaling directly in JVM memory.

---

## 🔬 Benchmark Suites

1. **`EnvelopeEncryptionBenchmark.java`**:
   - Compares **Plaintext DB writes** vs. **Local AES-256-GCM** vs. **Vault KMS Envelope Encryption**.
   - Quantifies cryptographic math vs. KMS network IPC overhead.
2. **`CryptoShredVsPhysicalDeleteBenchmark.java`**:
   - Evaluates **$O(1)$ constant-time key revocation** vs. **$O(N)$ linear relational database cascading deletions** across $N \in \{10, 100, 1000, 5000, 10000\}$ patient visits.
3. **`MerkleTreeBenchmark.java`**:
   - Measures single leaf insertion, binary DAG root computation, and RSA-2048 deletion inclusion proof verification for trees with up to $100,000$ historical deletion events.

---

## 🏃 How to Run

```bash
# Run all microbenchmarks and generate LaTeX tables
./benchmarks/micro-jmh/run-benchmarks.sh

# Run a specific benchmark (e.g. CryptoShred vs Physical Delete)
./benchmarks/micro-jmh/run-benchmarks.sh CryptoShredVsPhysicalDeleteBenchmark
```

---

## 📂 Output Artifacts (`results/`)

* [`results.json`](./results/results.json): Full raw JMH JSON benchmark dataset.
* [`results.csv`](./results/results.csv): Tabular CSV dataset of scores, errors, and percentiles.
* `parse_results.py`: Python CLI parser converting JSON output into formatted LaTeX tables.
* `generate_charts.py`: Matplotlib visual curve plotting script (`deletion_complexity_curve.png`, `merkle_scaling_curve.png`).
