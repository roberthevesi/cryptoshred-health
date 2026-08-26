# Tier 2: Multi-User System Load Testing (Macro) — CryptoShred Health

This module contains the **system-level macro load testing harness** measuring end-to-end HTTP throughput, tail latency percentiles ($p_{50}, p_{90}, p_{95}, p_{99}$), Redis L2 cache hit ratios, Kafka event log ingestion, and fail-safe zero data leakage under concurrent clinical workloads (1 to 500 Virtual Users).

---

## 🔬 Test Scenarios

1. **Scenario 1: High-Concurrency Encrypted Reads (Cache Hits vs. KMS Decryption)**
   - 85% cache-hit ratio evaluating Redis L2 cache speed vs PostgreSQL + Vault KMS unwrap.
2. **Scenario 2: High-Concurrency Encrypted Ingestion (Writes)**
   - Full clinical encounters with dynamic DEK generation, AES-256-GCM envelope encryption, DB persistence, and Kafka event streaming.
3. **Scenario 3: GDPR Article 17 Crypto-Shredding Under Concurrency**
   - Vault KEK destruction, DB redaction, Redis key eviction, Merkle tree leaf insertion, and RSA-2048 signing under simultaneous deletion load.
4. **Scenario 4: Fail-Safe Post-Shred Verification**
   - Concurrent read attempts against shredded visits verifying 100% fail-safe fallback and zero data leakage.

---

## 🏃 How to Run

```bash
# Execute full multi-user load test suite (1 to 500 VUs)
./benchmarks/macro-system/run-load-tests.sh

# Or run the Node.js harness directly
node benchmarks/macro-system/load-test-harness.mjs

# Or run with native k6
k6 run benchmarks/macro-system/k6-script.js
```

---

## 📂 Output Artifacts (`results/`)

* [`load_test_report.md`](./results/load_test_report.md): Academic report formatted for Chapter 5 of the dissertation with 5 LaTeX tables.
* [`raw_metrics.json`](./results/raw_metrics.json): Complete JSON dataset across all concurrency tiers and scenarios.
* [`metrics.csv`](./results/metrics.csv): Tabular CSV dataset of RPS, error rates, and latency percentiles.
