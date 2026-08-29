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

When Grafana boots up, the following dashboards are automatically loaded under folder **`CryptoShred`**:

### 1. 🔐 Cryptographic Engine & Security Telemetry (`cryptoshred_security.json`)
- **Crypto Operations Rate & Throughput**: Real-time rate of AES-GCM encryption, decryption, KEK rotation, and crypto-shredding operations.
- **Crypto Engine Latencies**: Real-time latency tracking for encryption/decryption operations.
- **Merkle Proof Minting & Verification**: Deletion certificate generation duration across `PATIENT_PROFILE`, `CLINICAL_VISIT`, and `INCLUSION_PROOF` scopes.
- **HMAC Blind Index Search Rates**: Search lookup counts partitioned by blind indexed fields (`nhs_number`, `mrn`, `last_name`).
- **Resurrected Key Tombstone Purges**: Audit metrics tracking automated reconciliation of orphan Vault KMS keys against Merkle tombstones.
- **Vault KMS Telemetry**: Transit engine handle request rates, token operations, and rollback metrics.

### 2. 🏥 Clinical EHR Throughput & JVM Health (`cryptoshred_clinical.json`)
- **API Request Throughput**: Spring Boot HTTP server request rates (`/api/**`, `/actuator/**`).
- **Endpoint Latencies**: Per-route response times and HTTP status distribution.
- **JVM Memory Distribution**: Live Heap vs Non-Heap memory usage, committed vs max buffers.
- **Garbage Collection Dynamics**: GC pause durations and collector timings.
- **JVM Threads & CPU**: Active threads, daemon threads, and process/system CPU utilization.
- **HikariCP Connection Pool**: Active, idle, and pending PostgreSQL JDBC connections.

### 3. 🌐 Distributed Infrastructure & Cluster Telemetry (`cryptoshred_infrastructure.json`)
- **PostgreSQL Database Engine**: Active backend connections, commit/rollback transaction rates, and buffer cache hit ratio.
- **Redis In-Memory Cache**: Connected clients, memory footprint, max memory headroom, and evictions.
- **Apache Kafka Event Log**: Broker cluster state, topic partition topologies, and event streaming metrics.
- **HashiCorp Vault Raft Cluster**: Active node leader status and cluster health.

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
