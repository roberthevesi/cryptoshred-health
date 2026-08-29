# 📊 CryptoShred Health — Distributed Observability & Cryptographic Monitoring Stack

This directory contains turnkey Prometheus scraping configurations, automated Grafana provisioning (datasources & dashboards), and infrastructure exporter telemetry definitions for **CryptoShred Health**.

---

## 🏗️ Architecture & Metric Pipelines

```
                                  ┌──────────────────────────┐
                                  │   Grafana Dashboards     │
                                  │   (Port 3000 / admin)    │
                                  └─────────────┬────────────┘
                                                │ PromQL
                                  ┌─────────────▼────────────┐
                                  │  Prometheus TSDB Engine  │
                                  │       (Port 9090)        │
                                  └─────────────┬────────────┘
         ┌───────────────────┬──────────────────┼──────────────────┬──────────────────┐
         │ (5s scrape)       │ (10s scrape)     │ (10s scrape)     │ (10s scrape)     │ (10s scrape)
┌────────▼────────┐ ┌────────▼────────┐ ┌───────▼────────┐ ┌───────▼────────┐ ┌───────▼────────┐
│  Spring Boot    │ │ HashiCorp Vault │ │   PostgreSQL   │ │     Redis      │ │ Apache Kafka   │
│  Actuator +     │ │ Transit KMS     │ │    Exporter    │ │    Exporter    │ │    Exporter    │
│  Micrometer     │ │ /v1/sys/metrics │ │  (Port 9187)   │ │  (Port 9121)   │ │  (Port 9308)   │
│  (Port 8080)    │ │  (Port 8200)    │ └───────┬────────┘ └───────┬────────┘ └───────┬────────┘
└─────────────────┘ └─────────────────┘         │                  │                  │
                                        ┌───────▼────────┐ ┌───────▼────────┐ ┌───────▼────────┐
                                        │  PostgreSQL 15 │ │ Redis Cluster  │ │ Kafka Broker   │
                                        │  (Port 5432)   │ │ (Port 6379)    │ │ (Port 29092)   │
                                        └────────────────┘ └────────────────┘ └────────────────┘
```

---

## 🚀 One-Command Turnkey Startup

### Start All Core Services + Monitoring Stack
```bash
docker compose --profile monitoring --profile default up -d
```
*(or spin up the full containerized stack including the backend container: `docker compose --profile all up -d`)*

### Start Only the Monitoring Stack & Exporters
```bash
docker compose --profile monitoring up -d
```

---

## 🧭 Pre-Provisioned Grafana Dashboards

When Grafana boots up, the following **2 streamlined, intuitive dashboards** are automatically loaded under folder **`CryptoShred`**:

### 1. 🏥 Executive & Cryptographic Overview (`cryptoshred_overview.json`)
- **Key Performance Stat Cards**: Live rates for active crypto ops/sec, average encryption/decryption latencies in ms, total Merkle deletion proofs minted, blind index searches/sec, and tombstone resurrection alerts (`✅ 0 Healthy`).
- **Cryptographic Latency Curves**: Millisecond-level tracking of AES-256-GCM envelope encryption vs decryption vs key rotation vs $\mathcal{O}(1)$ crypto-shredding.
- **Merkle Proof Minting Latency**: Directional binary Merkle DAG proof generation duration.
- **Clinical API Throughput**: Request rate (req/s) broken down across `/api/patients`, `/api/erasure`, `/api/gp`, `/api/fhir`.
- **HMAC Blind Index Searches**: High-speed $\mathcal{O}(1)$ B-tree lookups over `nhs_number`, `mrn`, and `last_name`.

### 2. ⚙️ Cluster Infrastructure & Storage Health (`cluster_infrastructure.json`)
- **JVM Heap Allocation & Memory Zeroization**: Heap memory usage (used vs committed vs max) confirming scoped zeroization leaves zero residual memory leaks.
- **Garbage Collection Dynamics**: GC pause durations in milliseconds.
- **HikariCP PostgreSQL Connection Pool**: Active, idle, and pending database JDBC connections.
- **Redis L2 Cache & Kafka Event Log**: Memory footprint, connected clients, and streaming event throughput.

---

## 🔌 Port Mappings & Default Access

| Service | Port | Metric / UI Path | Default Credentials |
| :--- | :--- | :--- | :--- |
| **Grafana** | `3000` | `http://localhost:3000` | `admin` / `admin` |
| **Prometheus** | `9090` | `http://localhost:9090` | *None* |
| **Spring Boot Actuator** | `8080` | `http://localhost:8080/actuator/prometheus` | *None (Public Actuator)* |
| **HashiCorp Vault (Node 1)**| `8200` | `http://localhost:8200/v1/sys/metrics?format=prometheus` | Token: `root-vault-token` |
| **PostgreSQL Exporter** | `9187` | `http://localhost:9187/metrics` | *None* |
| **Redis Exporter** | `9121` | `http://localhost:9121/metrics` | *None* |
| **Kafka Exporter** | `9308` | `http://localhost:9308/metrics` | *None* |

---

## 🔍 Verification & Health Checks

### Check Actuator Prometheus Metrics on Backend:
```bash
curl -s http://localhost:8080/actuator/prometheus | grep "cryptoshred_"
```

### Check Prometheus Active Scrape Targets:
Open `http://localhost:9090/targets` or run:
```bash
curl -s http://localhost:9090/api/v1/targets | jq .
```

### Check Grafana Dashboards:
Open `http://localhost:3000/dashboards` in your browser and browse the **CryptoShred** folder.
