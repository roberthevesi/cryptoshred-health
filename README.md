# 🏥 Beyond Physical Deletion: Enforcing Verifiable Cryptographic Erasure Across Distributed Storage Layers in Zero-Plaintext Electronic Health Record Architectures
### *(CryptoShred Health)*

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![HashiCorp Vault 1.13](https://img.shields.io/badge/Vault%20Raft%20KMS-1.13-blue.svg)](https://www.vaultproject.io/)
[![React 19](https://img.shields.io/badge/React-19-61dafb.svg)](https://react.dev/)
[![Prometheus & Grafana](https://img.shields.io/badge/Observability-Prometheus%20%7C%20Grafana-F46800.svg)](https://grafana.com/)
[![Tests](https://img.shields.io/badge/Tests-144%2F144%20Passing-success.svg)](https://github.com/)

**CryptoShred Health** is an enterprise-grade, zero-knowledge Electronic Health Record (EHR) platform engineered to resolve the fundamental conflict between **GDPR Article 17 (Right to be Forgotten)** and **statutory medical retention laws** (such as UK NHS 8-year and HIPAA compliance rules).

By combining **envelope encryption under HashiCorp Vault 3-Node Raft KMS**, **Post-Quantum Hybrid Signatures (NIST FIPS 204 ML-DSA-65)**, **Binary Merkle DAG audit ledgers**, **HMAC-SHA256 Blind Indexing**, and **out-of-band WORM Disaster Recovery**, the platform guarantees immediate $\mathcal{O}(1)$ cryptographic irrecoverability ($2^{-256}$ bound) without destroying physical append-only compliance archives.

---

## 🧭 System Architecture

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

---

## 🌟 Core Technical Highlights

### 1. 🔒 Zero-Plaintext Database & HMAC Blind Indexing
* **Strict Ciphertext Storage**: All Protected Health Information (PHI), demographic PII, clinical notes, and biometrics are stored as AES-256-GCM ciphertext blobs (`encrypted_data_blob`) with **AAD Context Binding** (`patientId` / `visitId`).
* **Blind Index Search**: Deterministic salted HMAC-SHA256 hashes enable fast $\mathcal{O}(1)$ B-tree queries over `NHS Number`, `MRN`, and `Surname` without decrypting the database or exposing plaintext to administrators.

### 2. ⚡ High-Availability KMS (3-Node Vault Raft Cluster)
* **Consensus Replication**: Integrated 3-node HashiCorp Vault cluster (`vault-1`, `vault-2`, `vault-3`) replicating Key Encryption Keys (KEKs) via Raft consensus logs.
* **HAProxy Auto-Failover Router (`:8200`)**: Actively polls nodes every 500ms via `/v1/sys/health` and automatically re-routes traffic to the elected active leader ($T_{\text{failover}} < 2.5\text{ s}$ with 0% dropped requests).

### 3. 🌲 Directional Binary Merkle DAG & Proofs
* Persistent binary Merkle tree in PostgreSQL (`merkle_nodes`) records all deletion proofs.
* Self-describing directional proof paths (`"L:"` / `"R:"`) enable independent third-party verification against the canonical Merkle root.

### 4. 🔮 Post-Quantum Hybrid Digital Signatures (NIST FIPS 204)
* Dual hybrid asymmetric proof signing combining **Vault RSA-2048** (`SHA256withRSA`) + **ML-DSA-65 (CRYSTALS-Dilithium3)** via BouncyCastle PQC, ensuring forward secrecy against future quantum adversaries.

### 5. 🛡️ Disaster Recovery & Snapshot Restoration Reconciliation
* **Atomic Bundles**: Coupled DB dump + Vault Raft snapshot + WORM encounters (`bundle_YYYY-MM-DD/`) cryptographically sealed with `bundle_manifest.json`.
* **Solving the Snapshot Restoration Paradox**: Out-of-band WORM harvesting protocol (`backups/deletion-receipt_*.json`) detects resurrected Vault KEKs on restore or cold boot, immediately destroying them to preserve cryptographic erasure guarantees.

### 6. 📜 Policy-Driven Retention Governance & HL7 FHIR R4
* Dynamic rolling retention horizon: $\max(\text{patient.createdAt}, \max_{v}(\text{visit.createdAt})) + \Delta_{\text{retention}}$.
* Admin settings support UK NHS (8-year), HIPAA (6-year), and Pediatric (25-year) statutory horizons.
* Full **HL7 FHIR R4 Collection Bundle** export (`/api/patients/{id}/fhir`) for clinical interoperability.

---

## 🚀 Quick Start Guide

CryptoShred Health supports two execution modes via Docker Compose:

### 🌟 Mode 1: One-Click Turnkey Demo (Full Docker Stack)
Ideal for evaluations, examiner testing, and demos with zero local development prerequisites:

```bash
docker compose --profile all up -d --build
```
* **Clinical Web UI:** [http://localhost:5173](http://localhost:5173)
* **Spring Boot API Swagger:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **Grafana Mission Control:** [http://localhost:3000](http://localhost:3000) (`admin` / `admin`)
* **Kafka UI:** [http://localhost:8085](http://localhost:8085)
* **Vault Primary UI:** [http://localhost:8200/ui/vault/](http://localhost:8200/ui/vault/)

---

### 💻 Mode 2: Development & Benchmarking Mode
Ideal for rapid local development (Vite HMR, backend debugging) and bare-metal **JMH microbenchmarking**:

#### Step 1: Start Infrastructure & Monitoring
```bash
docker compose --profile monitoring up -d
```

#### Step 2: Configure Environment Variables
Verify `.env` in repository root and `backend/.env`:
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

#### Step 3: Run Backend (Java 21 / Spring Boot 3.3.4)
```bash
cd backend
./mvnw spring-boot:run
```

#### Step 4: Run Frontend (React 19 + Vite)
```bash
cd frontend/cryptoshred-health
npm install
npm run dev
```

---

## 🛑 Safe Container Lifecycle (Preserving Data & Keys)

> [!WARNING]
> **Never run `docker compose down -v`**. The `-v` flag permanently deletes Docker named volumes (`pgdata`, `vaultdata-*`), destroying database records and Vault master keys.

* **Stop all infrastructure & monitoring safely**:
  ```bash
  docker stop $(docker ps --format '{{.Names}}' | grep -vE 'backend|frontend')
  ```
* **Start back up without losing data**:
  ```bash
  docker start $(docker ps -a --format '{{.Names}}' | grep -vE 'backend|frontend')
  ```
  *(or `docker compose --profile monitoring up -d`)*

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
| **Vault Direct Nodes** | `8201`–`8203` | [http://localhost:8201/ui/](http://localhost:8201/ui/) | Token: `root` |
| **PostgreSQL 15** | `5433` | `localhost:5433/healthdb` | `root` / `toor` |
| **Redis 7 (L2 Cache)** | `6379` | `localhost:6379` | None |
| **Kafka KRaft Broker**| `9092` | `localhost:9092` | None |
| **Kafka Web UI** | `8085` | [http://localhost:8085](http://localhost:8085) | Public |
| **Redis Insight UI** | `5540` | [http://localhost:5540](http://localhost:5540) | Public |

---

## 👥 Default Demo Accounts (RBAC)

| Role | Email | Password | Allowed Scope |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin@cryptoshred.health` | `Password123!` | Staff provisioning, synthetic data seeding (`/api/admin/seed-data`), KMS key rotation (`/api/keys/rotate`), DR backup & restore |
| **DOCTOR** | `doctor@hospital.com` | `Password123!` | Patient intake, clinical SOAP encounters, PDF attachments, GDPR Art. 17 crypto-shredding |
| **AUDITOR** | `auditor@health.gov` | `Password123!` | View anonymized records, verify Merkle DAG deletion proofs, audit WORM receipts |

---

## 🛡️ Disaster Recovery REST Commands

To capture and restore disaster recovery bundles:

```bash
# 1. Login as Admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cryptoshred.health","password":"Password123!"}' \
  | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# 2. Capture Atomic Bundle (PostgreSQL + Vault + WORM)
curl -s -X POST http://localhost:8080/api/admin/backups/bundle \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .

# 3. Restore Bundle (Automatically reconciles Merkle tombstones & purges resurrected KEKs)
curl -s -X POST http://localhost:8080/api/admin/backups/bundles/<BUNDLE_ID>/restore \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .
```

---

## 🧪 Automated Verification Suite

```bash
# Backend test suite (144/144 tests passing)
cd backend
./mvnw test

# Frontend production typecheck & build
cd frontend/cryptoshred-health
npm run build

# Publication Figure Generator (renders vector PDFs to thesis/figures/)
python3 utils/generate_figures.py --all

# Visual Dissertation Comparison Visualizer
python3 utils/build_comparison_app.py
```

---

## 📂 Repository Structure

```text
cryptoshred-health/
├── backend/            # Spring Boot 3.3.4 (Java 21, Vault KMS, Kafka, Redis, JPA)
├── frontend/           # React 19 + Vite + Tailwind CSS SPA
├── monitoring/         # Prometheus scrape targets & Grafana pre-provisioned dashboards
├── thesis/             # LaTeX Dissertation sources, chapters, and publication vector figures
├── utils/              # Publication figure generator & visual comparison application
├── scripts/            # Operator DR shell scripts (backup, restore, unseal)
└── docker-compose.yml  # Multi-profile distributed orchestration
```

---

## 📄 License & Academic Attribution

Developed by **Robert Hevesi** as part of the Master's Dissertation in Software Engineering:  
*“Beyond Physical Deletion: Enforcing Verifiable Cryptographic Erasure Across Distributed Storage Layers in Zero-Plaintext Electronic Health Record Architectures (CryptoShred Health)”*  
Universitatea Politehnica din Timișoara — Faculty of Automation and Computers.
