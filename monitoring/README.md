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

## 🧭 Pre-Provisioned Grafana Dashboard

When Grafana boots up, a single, all-in-one **Unified Mission Control Dashboard** is automatically loaded under folder **`CryptoShred`**:

### 🏥 Unified Mission Control (`cryptoshred_unified.json`)
* **🧩 System Component Status**: Live 5-card status banner across Spring Boot API (`:8080`), Vault KMS Primary (`:8200`), PostgreSQL DB (`:5433`), Redis L2 Cache (`:6379`), and Kafka Event Log (`:9092`).
* **🔐 Cryptographic & Clinical KPIs**: Live numbers for active crypto ops/sec, average AES-256-GCM encryption/decryption times in ms, Merkle deletion proofs minted, and tombstone resurrection alerts (`✅ 0 Healthy`).
* **📈 Real-Time Operation Latencies**: Millisecond latency curves for encryption, decryption, key rotation, and $\mathcal{O}(1)$ crypto-shredding alongside clinical REST API request rates (`/api/patients`, `/api/erasure`, `/api/gp`, `/api/fhir`).
* **🧠 JVM Heap & Memory Zeroization**: Clean, aggregated heap memory curves (`sum(jvm_memory_used_bytes)`) and clear average GC pause durations.
* **🗄️ Database & Storage Telemetry**: HikariCP connection pool (Active vs Idle vs Pending) and deduplicated `healthdb` transaction throughput (Commits/sec vs Rollbacks/sec).
* **⚡ Redis & Kafka Activity**: Redis memory usage and Kafka topic partition metrics.

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
