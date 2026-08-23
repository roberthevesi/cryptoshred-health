# CryptoShred Health — Enterprise Healthcare EHR with Vault KMS Crypto-Shredding

**CryptoShred Health** is an Electronic Health Record (EHR) platform featuring cryptographic Right-to-be-Forgotten (GDPR Article 17) compliance through **HashiCorp Vault KMS envelope encryption**, Apache Kafka event streaming, and a patient-first primary care workflow.

---

## 🌟 Key Architecture & Features

- **Clinical Primary Care EHR Workflow**: Patient Census directory linking to dedicated patient workspaces (`/patients/:patientId`) with encounter timelines, SOAP consultation notes, telemetry, and PDF attachments.
- **Envelope Encryption with HashiCorp Vault KMS**: Every patient identity and clinical encounter generates a dedicated 256-bit AES Data Encryption Key (DEK), wrapped via patient-scoped Vault Transit Key Encryption Keys (`patient_{patientUuid}` and `patient_{patientUuid}_visit_{visitUuid}`).
- **Cryptographic Key Rotation**: Built-in zero-plaintext DEK re-wrapping (`POST /api/keys/rotate`) under newly rotated Vault KEK versions without decrypting or exposing underlying clinical records.


- **Cryptographic Right-to-be-Forgotten (GDPR Article 17)**: Instant, permanent erasure by destroying the Vault Transit KEK, rendering ciphertext cryptographically irrecoverable, and minting digital deletion proof certificates.
- **GP Directory & Management**: Practitioner registry with GMC license tracking and searchable assignment dropdowns.
- **Kafka Streaming & Redis Cache**: Event stream auditing (`patient-record-events`) and fast cached reads with auto-invalidation on crypto-shred.
- **Clinical Light Design System**: Clean NHS-style healthcare UI.

---

## 🏗️ Tech Stack

- **Backend**: Spring Boot 3.3.4, Java 21, Spring Security (Stateless JWT), Spring Data JPA, Spring Vault, Spring Kafka, Spring Data Redis
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS, TanStack Query v5, Lucide Icons, Axios
- **Datastores & Infrastructure**: PostgreSQL 15, HashiCorp Vault, Apache Kafka (KRaft), Provectus Kafka UI, Redis 7

---

## 🚀 Quick Start

### 1. Start Infrastructure Containers
```bash
# Start PostgreSQL, Vault, Kafka, and Redis
docker compose up -d
```

### 2. Configure Environment Variables
Create `.env` in root and `backend/.env`:
```env
POSTGRES_DB=healthdb
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_PORT=5433
JWT_SECRET=dGhpcy1pcy1hLXNlY3VyZS0yNTYtYml0LXNlY3JldC1rZXktZm9yLWhlYWx0aGNhcmUtZWhyCg==
VAULT_DEV_ROOT_TOKEN=root
VAULT_PORT=8200
REDIS_HOST=localhost
REDIS_PORT=6379
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

### 3. Run Backend (Spring Boot)
```bash
cd backend
./mvnw spring-boot:run
```
*API available at: `http://localhost:8080` (Swagger UI: `http://localhost:8080/swagger-ui.html`)*

### 4. Run Frontend (Vite)
```bash
cd frontend/cryptoshred-health
npm install
npm run dev
```
*Web dashboard available at: `http://localhost:5173`*

---

## 🔒 Verification & Testing
```bash
# Backend test suite
cd backend
mvn test

# Frontend type check and production bundle
cd frontend/cryptoshred-health
npm run build
```