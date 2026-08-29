# 🏥 CryptoShred Health — Zero-Knowledge Healthcare EHR & Cryptographic Deletion Architecture

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![HashiCorp Vault 1.13](https://img.shields.io/badge/Vault%20Raft%20KMS-1.13-blue.svg)](https://www.vaultproject.io/)
[![React 19](https://img.shields.io/badge/React-19-61dafb.svg)](https://react.dev/)
[![Prometheus & Grafana](https://img.shields.io/badge/Observability-Prometheus%20%7C%20Grafana-F46800.svg)](https://grafana.com/)
[![Tests](https://img.shields.io/badge/Tests-128%2F128%20Passing-success.svg)](https://github.com/)

**CryptoShred Health** is an enterprise-grade, zero-knowledge Electronic Health Record (EHR) platform engineered to resolve the legal paradox between **GDPR Article 17 (Right to be Forgotten)** and **NHS / HIPAA 8-Year Immutable Record Retention Laws**.

By leveraging **HashiCorp Vault 3-Node Raft Transit KMS envelope encryption**, **Post-Quantum Hybrid Signatures (NIST FIPS 204 ML-DSA-65)**, **Binary Merkle DAG audit ledgers**, **HMAC-SHA256 Blind Indexing**, and **Atomic Coordinated Disaster Recovery**, CryptoShred Health guarantees immediate $\mathcal{O}(1)$ cryptographic irrecoverability ($2^{-256}$ bound) without physically destroying append-only WORM compliance archives.

---

## 🌟 Key Architecture & Capabilities

```
                                  ┌──────────────────────────┐
                                  │ 🏥 Unified Mission       │
                                  │ Control (Grafana :3000)  │
                                  └─────────────┬────────────┘
                                                │ PromQL
                                  ┌─────────────▼────────────┐
                                  │ Prometheus TSDB (:9090)  │
                                  └─────────────┬────────────┘
         ┌───────────────────┬──────────────────┼──────────────────┬──────────────────┐
         │ (5s scrape)       │ (10s scrape)     │ (10s scrape)     │ (10s scrape)     │ (10s scrape)
┌────────▼────────┐ ┌────────▼────────┐ ┌───────▼────────┐ ┌───────▼────────┐ ┌───────▼────────┐
│  Spring Boot    │ │  Vault Cluster  │ │   PostgreSQL   │ │     Redis      │ │ Apache Kafka   │
│  Actuator +     │ │  (Nodes 1,2,3)  │ │    Exporter    │ │    Exporter    │ │    Exporter    │
│  Micrometer     │ │  /v1/sys/metrics│ │  (Port 9187)   │ │  (Port 9121)   │ │  (Port 9308)   │
│  (Port 8080)    │ └────────┬────────┘ └───────┬────────┘ └───────┬────────┘ └───────┬────────┘
└────────┬────────┘          │                  │                  │                  │
         │                   │                  │                  │                  │
         │                   ▼                  │                  │                  │
         │         ┌───────────────────┐        │                  │                  │
         │         │ HAProxy Vault LB  │        │                  │                  │
         │         │    (Port 8200)    │        │                  │                  │
         │         └─────────┬─────────┘        │                  │                  │
         │                   │                  │                  │                  │
         ▼                   ▼                  ▼                  ▼                  ▼
┌─────────────────┐ ┌─────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ Clinical API    │ │ 3-Node Raft KMS │ │ PostgreSQL 15  │ │ Redis L2 Cache │ │ Kafka KRaft    │
│ Spring Boot Core│ │ (8201/8202/8203)│ │ (Port 5433)    │ │ (Port 6379)    │ │ (Port 9092)    │
└─────────────────┘ └────────────────┘ └────────────────┘ └────────────────┘ └────────────────┘
```

### 1. 🔒 Zero-Plaintext Database Storage & Blind Indexing
* **100% Zero Plaintext**: All Protected Health Information (PHI), demographic PII, clinical encounter notes, and medical records are stored strictly inside AES-256-GCM ciphertext blobs (`encrypted_data_blob`) with **AAD Context Binding** (`patientId` / `visitId`).
* **HMAC-SHA256 Blind Indexing**: Deterministic salted hashes enable fast $\mathcal{O}(1)$ B-tree database searchability across `NHS Number`, `MRN`, and `Last Name` without exposing plaintext PII to database administrators.

### 2. ⚡ 3-Node HashiCorp Vault Raft HA Cluster & Auto-Failover
* **Raft Consensus Replication**: High Availability KMS cluster across 3 nodes (`vault-1`, `vault-2`, `vault-3`) replicating Transit Key Encryption Keys (KEKs) via Raft consensus logs in real time.
* **HAProxy Auto-Failover Router (`vault-lb :8200`)**: Performs active `/v1/sys/health` probes every 500ms, routing 100% of traffic directly to whichever node is the elected active leader with sub-second failover.

### 3. 🌲 Binary Merkle DAG & Directional Deletion Proofs
* Persistent binary Merkle tree in PostgreSQL (`merkle_nodes`) mints tamper-evident inclusion and deletion proofs.
* Self-describing directional proof paths (`"L:"` / `"R:"`) allow independent verification of data state.

### 4. 🔮 Post-Quantum Cryptographic Proof Signing (NIST FIPS 204)
* Dual hybrid asymmetric proof signing combining **Vault RSA-2048** (`SHA256withRSA`) + **ML-DSA-65 (CRYSTALS-Dilithium3)** via BouncyCastle PQC for post-quantum security.

### 5. 🛡️ Atomic Coordinated Disaster Recovery & Tombstone Reconciler
* Coupled DB + Vault + WORM atomic bundles (`bundle_YYYY-MM-DD/`) with `bundle_manifest.json` and dual digital signatures.
* **Merkle Tombstone Reconciler**: High-performance $O(1)$ startup and post-restore reconciler that checks Vault KMS and purges resurrected keys for previously shredded patients.

### 6. 📊 Distributed Observability Stack
* **Unified Mission Control Dashboard** in Grafana (`:3000`) with live 5-service health banner, 3-node dynamic leader indicators, crypto latency curves (AES-GCM, Merkle minting, key rotation), and JVM memory zeroization tracking.
* Complete Prometheus TSDB metrics exported across Spring Boot Actuator, Vault KMS, PostgreSQL, Redis, and Kafka.

---

## 🏗️ Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend Core** | Java 21, Spring Boot 3.3.4, Spring Security 6 (Stateless JWT), Spring Data JPA, Spring Vault, Spring Kafka, Spring Data Redis, Micrometer, BouncyCastle PQC (ML-DSA-65) |
| **Frontend UI** | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query v5, Lucide Icons, Axios |
| **Key Management (KMS)** | 3-Node HashiCorp Vault 1.13 (Raft Storage), HAProxy 2.8 Load Balancer |
| **Datastores & Streaming** | PostgreSQL 15, Redis 7 (L2 Cache), Apache Kafka 3.7 (KRaft mode) |
| **Observability & Metrics** | Prometheus v2.51, Grafana 10.4, Postgres Exporter, Redis Exporter, Kafka Exporter |

---

## 🚀 Quick Start Guide

### 1. Start Infrastructure & Observability Containers
```bash
# Start Core Datastores + 3-Node Vault Raft Cluster + Prometheus/Grafana Monitoring Stack
docker compose --profile monitoring up -d
```

### 2. Configure Environment Variables
Create `.env` in the repository root and `backend/.env`:
```env
POSTGRES_DB=healthdb
POSTGRES_USER=root
POSTGRES_PASSWORD=toor
POSTGRES_PORT=5433
JWT_SECRET=dGhpcy1pcy1hLXNlY3VyZS0yNTYtYml0LXNlY3JldC1rZXktZm9yLWhlYWx0aGNhcmUtZWhyCg==
VAULT_DEV_ROOT_TOKEN=root
VAULT_PORT=8200
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_TTL_MS=900000
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

### 3. Run Backend (Spring Boot 3.3.4)
```bash
cd backend
./mvnw spring-boot:run
```
* API Server: `http://localhost:8080`
* Swagger UI Docs: `http://localhost:8080/swagger-ui.html`
* Prometheus Actuator: `http://localhost:8080/actuator/prometheus`

### 4. Run Frontend (React 19 + Vite)
```bash
cd frontend/cryptoshred-health
npm install
npm run dev
```
* Clinical Dashboard: `http://localhost:5173`

---

## 🌐 Network & Port Reference

| Service | Port | Endpoint / URL | Default Credentials |
| :--- | :---: | :--- | :--- |
| **Clinical Web App** | `5173` | [http://localhost:5173](http://localhost:5173) | Doctor / Auditor / Patient / Admin |
| **Spring Boot API** | `8080` | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | JWT Bearer Token |
| **Grafana Dashboards** | `3000` | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` |
| **Prometheus TSDB** | `9090` | [http://localhost:9090/targets](http://localhost:9090/targets) | Public |
| **Vault HA Proxy** | `8200` | [http://localhost:8200/ui/vault/](http://localhost:8200/ui/vault/) | Token: `root` |
| **HAProxy Live Stats** | `8209` | [http://localhost:8209/stats](http://localhost:8209/stats) | Public |
| **Vault Node 1 (Direct)** | `8201` | [http://localhost:8201/ui/](http://localhost:8201/ui/) | Token: `root` |
| **Vault Node 2 (Direct)** | `8202` | [http://localhost:8202/ui/](http://localhost:8202/ui/) | Token: `root` |
| **Vault Node 3 (Direct)** | `8203` | [http://localhost:8203/ui/](http://localhost:8203/ui/) | Token: `root` |
| **PostgreSQL 15** | `5433` | `localhost:5433/healthdb` | `root` / `toor` |
| **Redis 7** | `6379` | `localhost:6379` | None |
| **Kafka KRaft Broker**| `9092` | `localhost:9092` | None |
| **Kafka UI** | `8085` | [http://localhost:8085](http://localhost:8085) | Public |

---

## 👥 Default Demo Accounts (Role-Based Access Control)

| Role | Email | Password | Allowed Capabilities |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin@cryptoshred.health` | `Password123!` | Staff management, synthetic data seeder, KMS key rotation (`/api/keys`), DR backup capture & restore |
| **DOCTOR** | `doctor@hospital.com` | `Password123!` | Patient intake, clinical encounters, SOAP notes, PDF attachments, GDPR crypto-shredding |
| **AUDITOR** | `auditor@health.gov` | `Password123!` | View anonymized records, verify Merkle DAG deletion proofs, audit WORM receipts |
| **PATIENT** | `patient@health.org` | `Password123!` | View personal medical record and personal encounter timeline |

---

## 🛠️ Disaster Recovery CLI Tools

The project includes standalone operator scripts in [`scripts/`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/scripts/):

```bash
# 1. Capture atomic DB + Vault + WORM backup bundle with dual digital signatures
./scripts/backup-bundle.sh

# 2. Verify bundle SHA-256 integrity and RSA/PQC signatures
./scripts/verify-bundle.sh --bundle backups/bundles/bundle_2026-08-29_...

# 3. Restore bundle and trigger automatic Merkle Tombstone Reconciliation
./scripts/restore-bundle.sh --bundle bundle_2026-08-29_... --force

# 4. Unseal all 3 Vault cluster nodes
./scripts/unseal-vault.sh
```

---

## 🧪 Verification & Test Suite

```bash
# Run full JUnit 5 backend test suite (128/128 tests passing)
cd backend
./mvnw test

# Run frontend TypeScript typecheck and production build
cd frontend/cryptoshred-health
npm run build

# Run JMH Algorithmic Microbenchmarks (Tier 1)
./benchmarks/micro-jmh/run-benchmarks.sh

# Run Multi-User Macro Load Tests (Tier 2)
./benchmarks/macro-system/run-load-tests.sh
```

---

## 📄 License & Attribution

Developed by **Robert Hevesi** as part of the Master's Dissertation:  
*“Cryptographic Right-to-be-Forgotten in Healthcare Systems: Resolving the Conflict between GDPR and Immutable Medical Retention Laws”*.