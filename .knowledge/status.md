# SYSTEM STATUS & ARCHITECTURAL HANDBOOK

## 1. PROJECT SPECIFICATION & OBJECTIVES

* **Title:** Verifiable Cryptographic Data Shredding for GDPR/HIPAA "Right to Be Forgotten" Compliance in Distributed Healthcare Systems
* **Research Question:** How can cryptographic key destruction be combined with a verifiable audit protocol to provably guarantee GDPR/HIPAA-compliant data erasure across heterogeneous, append-only storage layers (relational DB, event log, cache, WORM backup) without requiring physical access to those layers?
* **Core Novelty:**
  1. **Heterogeneous Multi-Storage Protocol:** Simultaneous cross-layer data invalidation via a single KMS key destruction action.
  2. **Verifiable Proof Artifact:** A portable, cryptographically signed JSON proof artifact containing hashed access logs and storage coverage declarations for independent auditor verification.
  3. **Formally Scoped Threat Model:** Explicitly defining boundaries around pre-deletion plaintext cache leaks and WORM backup immutability.
* **Target Workspace Path:** `/Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health`

---

## 2. SYSTEM ARCHITECTURE & DATA FLOW

```
1. Patient Record Created / Updated
     ↓
2. EnvelopeEncryptionService generates random 256-bit DEK
     ↓
3. AES-256-GCM encrypts record payload (12-byte IV + 128-bit auth tag)
     ↓
4. VaultKmsService asks HashiCorp Vault: "Wrap DEK using patient's KEK"
     ↓
5. Ciphertext + Wrapped DEK + IV stored in PostgreSQL DB
     ↓
6. EventLogPublisher emits encrypted PatientRecordEventDto to Kafka ('patient-record-events' topic)
     ↓
[... Normal Operation: Read = Vault unwraps DEK → AES-256-GCM decrypts payload ...]
     ↓
7. "Forget Me" Triggered by Auditor
     ↓
8. VaultKmsService destroys patient's KEK in Vault Transit Engine
     ↓
9. EventLogPublisher emits RECORD_SHREDDED event to Kafka event log
     ↓
10. Wrapped DEKs in DB & Kafka Event Log become permanently un-decryptable
     ↓
11. ErasureService produces signed proof JSON with SHA-256 audit fingerprint
```

---

## 3. COMPONENT COMPLETION SUMMARY

| Plan Component | Status | Details |
|---|---|---|
| **1. Healthcare CRUD API** | 🟢 **100% Completed** | Spring Boot REST endpoints (Patients, Doctors, Auditors) |
| **2. Admin / Audit UI** | 🟢 **100% Completed** | React + TypeScript Dashboard & Deletion Proof Card |
| **3. Primary DB (PostgreSQL)** | 🟢 **100% Completed** | PostgreSQL 15 container running on port `5433` |
| **4. KMS Integration (Vault)** | 🟢 **100% Completed** | HashiCorp Vault container on port `8200` with auto-mounted `transit/` engine |
| **5. Envelope Encryption Engine** | 🟢 **100% Completed** | Real AES-256-GCM cipher suite & Vault KEK wrapping/unwrapping |
| **6. Append-Only Event Log (Kafka)** | 🟢 **100% Completed** | Apache Kafka container on port `9092` + Kafka UI on port `8085` + Spring Kafka publisher/consumer |
| **7. Distributed Cache (Redis)** | 🟡 **0% Completed** | **Next:** Redis container + Spring Data Redis cache service |
| **8. WORM Backup Exporter** | 🟡 **0% Completed** | **Next:** Daily scheduled JSON snapshot exporter |
| **9. Verifiable Proof Engine** | 🟡 **30% Completed** | Basic SHA-256 proof output. **Next:** Signed JSON artifact, Merkle log |
| **10. Evaluation & Benchmarks** | 🟡 **0% Completed** | Pending correctness tests & latency benchmarks |

---

## 4. CURRENT FILE INVENTORY & IMPLEMENTATION DETAILS

### 4.1 Infrastructure & Build Configuration
* [`docker-compose.yml`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/docker-compose.yml): Runs PostgreSQL 15 (`5433:5432`), HashiCorp Vault 1.13.3 (`8200:8200`), Apache Kafka 3.7.0 (`9092:9092`), and Kafka UI (`8085:8080`).
* [`pom.xml`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/pom.xml): Spring Boot 3.3.4 parent with `spring-vault-core`, `spring-kafka`, JWT (`jjwt 0.12.6`), JPA, Security, Lombok.
* [`application.properties`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/resources/application.properties): Datasource (`localhost:5433/healthdb`), Vault connection (`localhost:8200`), Kafka bootstrap servers (`localhost:9092`).

### 4.2 Backend Java Core (`/backend/src/main/java/com/roberthevesi/cryptoshred_health/`)
* **KMS & Cryptography:**
  * [`VaultConfig.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/config/VaultConfig.java): Extends `AbstractVaultConfiguration` to register `VaultOperations` bean.
  * [`VaultKmsService.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/service/VaultKmsService.java): Auto-mounts `transit/` engine (`@PostConstruct`), manages KEK creation (`ensureKeyExists`), DEK wrapping (`wrapDek`), unwrapping (`unwrapDek`), and permanent KEK deletion (`destroyKey`).
  * [`EnvelopeEncryptionService.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/service/EnvelopeEncryptionService.java): AES-256-GCM cipher suite generating 256-bit DEKs, 12-byte IVs, and 128-bit auth tags.
* **Event Logging (Kafka):**
  * [`EventLogPublisher.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/service/EventLogPublisher.java): Publishes `PatientRecordEventDto` to `patient-record-events` topic.
  * [`EventLogConsumer.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/service/EventLogConsumer.java): Consumes Kafka events for audit trail indexing.
  * [`PatientRecordEventDto.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/dto/PatientRecordEventDto.java): DTO holding `eventId`, `patientRecordId`, `eventType`, `vaultKeyName`, `wrappedDek`, `iv`, `encryptedDataBlob`, `timestamp`.
* **Business Logic & Persistence:**
  * [`PatientRecordService.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/service/PatientRecordService.java): Encrypts on create/update, unwraps & decrypts on fetch, publishes `RECORD_CREATED` and `RECORD_UPDATED` events to Kafka.
  * [`ErasureService.java`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/backend/src/main/java/com/roberthevesi/cryptoshred_health/service/ErasureService.java): Triggers `VaultKmsService.destroyKey` on "Forget Me", publishes `RECORD_SHREDDED` event to Kafka, redacts DB text, and returns deletion proof DTO.

### 4.3 Frontend React Dashboard (`/frontend/cryptoshred-health/src/`)
* [`DashboardPage.tsx`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/frontend/cryptoshred-health/src/pages/DashboardPage.tsx): Interacts with backend CRUD & erasure endpoints.
* [`DeletionProofCard.tsx`](file:///Users/roberthevesi/Coding/cryptoshred-health/cryptoshred-health/frontend/cryptoshred-health/src/components/DeletionProofCard.tsx): Displays signed proof card with SHA-256 audit fingerprint.

---

## 5. ROADMAP & IMMEDIATE NEXT STEPS

### 🎯 Step 2: Multi-Storage Layer Simulation (Remaining)
1. **Redis Cache:** Add Redis container to `docker-compose.yml` (port 6379) + `PatientRecordCacheService` in Spring Boot. Cache encrypted record responses.
2. **WORM Backup Exporter:** Implement `@Scheduled(cron = "59 59 23 * * *")` daily backup snapshot exporter writing read-only JSON snapshots (`backups/snapshot_YYYY-MM-DD.json`).

### 🎯 Step 3: Verifiable Deletion Proof Engine
1. Generate signed JSON proof artifacts containing Vault destruction timestamp, covered storage layers, and access log status.
2. Implement Merkle tree / hash chain over KMS & system access logs for anti-tamper proofing.

### 🎯 Step 4: Automated Evaluation & Benchmarks
1. Write automated test attempting post-shred decryption across DB, Kafka Log, Redis, and Backup Dump (verifying 100% failure).
2. Measure latency & throughput benchmarks comparing envelope encryption vs unencrypted baseline.
