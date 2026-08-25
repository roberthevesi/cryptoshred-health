package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import com.roberthevesi.cryptoshred_health.dto.ProofVerificationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientAttachment;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ErasureService — implements the Verifiable Crypto-Shredding (Right-to-be-Forgotten) pattern
 * for both entire Patient profiles and individual clinical visits.
 *
 * <p>Destroys encryption keys in HashiCorp Vault KMS, nullifies ciphertext blobs,
 * proactively evicts Redis caches, and emits Kafka deletion events.
 *
 * <p>Returns a {@link VerifiableDeletionProofDto} containing a digital RSA signature
 * and Merkle tree inclusion proof suitable for GDPR Article 17 compliance verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErasureService {

    private final PatientVisitRepository patientVisitRepository;
    private final PatientRepository patientRepository;
    private final EncryptionKeyRepository encryptionKeyRepository;
    private final VaultKmsService vaultKmsService;
    private final EventLogPublisher eventLogPublisher;
    private final PatientVisitCacheService patientVisitCacheService;
    private final ProofSigningService proofSigningService;
    private final MerkleTreeService merkleTreeService;

    /**
     * Complete Patient Right-to-be-Forgotten:
     * Destroys the patient's master demographic Vault KEK, shreds all linked clinical visits and attachments,
     * evicts Redis cache, and emits PATIENT_SHREDDED Kafka events.
     */
    @Transactional
    public VerifiableDeletionProofDto forgetPatient(String patientId, String requestedBy) {
        Patient patient = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));

        if (patient.isShredded()) {
            throw new IllegalStateException("Patient " + patientId + " has already been crypto-shredded");
        }

        LocalDateTime timestamp = LocalDateTime.now();
        String vaultKeyName = patient.getEncryptionKey() != null ? patient.getEncryptionKey().getVaultKeyName() : null;

        // 1. Destroy Patient Demographic Vault KEK
        if (patient.getEncryptionKey() != null) {
            destroyKeyInVault(patient.getEncryptionKey(), timestamp);
        }

        // 2. Redact Patient entity in PostgreSQL
        patient.setFirstName("[REDACTED]");
        patient.setLastName("[REDACTED]");
        patient.setDateOfBirth(null);
        patient.setGender("[REDACTED]");
        patient.setEmail(null);
        patient.setPhoneNumber(null);
        patient.setAddress(null);
        patient.setNhsNumber(null);
        patient.setEncryptedDataBlob(null);
        patient.setShredded(true);
        patient.setActive(false);
        patientRepository.save(patient);

        // 3. Shred all associated clinical visits & attachments
        List<PatientVisit> visits = patientVisitRepository.findByPatientIdentifier(patientId);
        for (PatientVisit visit : visits) {
            shredVisit(visit, timestamp);
        }

        // 4. Publish PATIENT_SHREDDED event to Kafka
        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .patientId(patientId)
                .eventType("PATIENT_SHREDDED")
                .vaultKeyName(vaultKeyName)
                .timestamp(timestamp)
                .build());

        // 5. Build immutable audit trail, Merkle proof, and RSA digital signature
        String auditTrail = buildPatientAuditTrail(patientId, visits.size(), requestedBy, timestamp);
        String sha256Hash = sha256Hex(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = buildCanonicalSignPayload(patientId, timestamp, sha256Hash, merkleRoot);
        String digitalSignature = proofSigningService.sign(canonicalPayload);

        log.info("Full Patient crypto-shred complete for {}. Proof hash: {}, Signature: {}", patientId, sha256Hash, digitalSignature);

        return VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .patientId(patientId)
                .vaultKeyName(vaultKeyName)
                .requestedBy(requestedBy)
                .timestamp(timestamp)
                .status("PATIENT_DELETED")
                .coveredStorageLayers(List.of("POSTGRES_DB", "KAFKA_EVENT_LOG", "REDIS_CACHE", "WORM_BACKUP"))
                .layerStatus(Map.of(
                        "POSTGRES_DB", "DEMOGRAPHICS_AND_VISITS_NULLIFIED",
                        "KAFKA_EVENT_LOG", "KEY_DESTROYED_PAYLOAD_UNREADABLE",
                        "REDIS_CACHE", "ALL_PATIENT_CACHES_EVICTED",
                        "WORM_BACKUP", "ZERO_PURGE_UNDECRYPTABLE"
                ))
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(digitalSignature)
                .build();
    }

    /**
     * Individual Clinical Visit Right-to-be-Forgotten:
     * Destroys the visit's Vault KEK, nullifies clinical payload and attachments,
     * evicts Redis cache, and emits VISIT_SHREDDED Kafka event.
     */
    @Transactional
    public VerifiableDeletionProofDto forgetVisit(UUID visitId, String requestedBy) {
        PatientVisit visit = patientVisitRepository.findById(visitId)
                .orElseThrow(() -> new IllegalArgumentException("Patient visit not found: " + visitId));

        if (visit.isShredded()) {
            throw new IllegalStateException("Visit " + visitId + " has already been shredded");
        }

        LocalDateTime timestamp = LocalDateTime.now();
        String vaultKeyName = visit.getEncryptionKey() != null ? visit.getEncryptionKey().getVaultKeyName() : null;

        shredVisit(visit, timestamp);

        // Publish VISIT_SHREDDED event to Kafka log
        eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .visitId(visitId)
                .patientId(visit.getPatient() != null ? visit.getPatient().getPatientId() : visit.getMrn())
                .eventType("VISIT_SHREDDED")
                .vaultKeyName(vaultKeyName)
                .timestamp(timestamp)
                .build());

        // Build audit trail, Merkle tree leaf, and RSA signature
        String auditTrail = buildVisitAuditTrail(visitId, requestedBy, timestamp);
        String sha256Hash = sha256Hex(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = buildCanonicalSignPayload(visitId.toString(), timestamp, sha256Hash, merkleRoot);
        String digitalSignature = proofSigningService.sign(canonicalPayload);

        log.info("Visit erasure complete for visit {}. Proof hash: {}, Signature: {}", visitId, sha256Hash, digitalSignature);

        return VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .visitId(visitId)
                .patientRecordId(visitId) // legacy alias
                .patientId(visit.getPatient() != null ? visit.getPatient().getPatientId() : visit.getMrn())
                .vaultKeyName(vaultKeyName)
                .requestedBy(requestedBy)
                .timestamp(timestamp)
                .status("VISIT_DELETED")
                .coveredStorageLayers(List.of("POSTGRES_DB", "KAFKA_EVENT_LOG", "REDIS_CACHE", "WORM_BACKUP"))
                .layerStatus(Map.of(
                        "POSTGRES_DB", "TEXT_NULLIFIED",
                        "KAFKA_EVENT_LOG", "KEY_DESTROYED_PAYLOAD_UNREADABLE",
                        "REDIS_CACHE", "CACHE_EVICTED",
                        "WORM_BACKUP", "ZERO_PURGE_UNDECRYPTABLE"
                ))
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(digitalSignature)
                .build();
    }

    private void shredVisit(PatientVisit visit, LocalDateTime timestamp) {
        // Step 1: Nullify sensitive clinical fields & attachments
        visit.setDiagnosis("[SHREDDED]");
        visit.setMedicalNotes("[SHREDDED]");
        visit.setAllergies("[SHREDDED]");
        visit.setPrescriptions("[SHREDDED]");
        visit.setChiefComplaint("[SHREDDED]");
        visit.setChronicConditions("[SHREDDED]");
        visit.setImmunizationStatus("[SHREDDED]");
        visit.setLifestyleFactors("[SHREDDED]");
        visit.setFollowUpDate(null);
        visit.setSoapSubjective("[SHREDDED]");
        visit.setSoapObjective("[SHREDDED]");
        visit.setSoapAssessment("[SHREDDED]");
        visit.setSoapPlan("[SHREDDED]");
        visit.setPatientName("[REDACTED]");
        visit.setAttendingDoctor("[REDACTED]");
        visit.setPhone(null);
        visit.setEmail(null);
        visit.setAddress(null);
        visit.setEmergencyContactName(null);
        visit.setEmergencyContactPhone(null);
        visit.setEncryptedDataBlob(null);
        visit.setShredded(true);

        if (visit.getAttachments() != null) {
            for (PatientAttachment attachment : visit.getAttachments()) {
                attachment.setEncryptedDataBlob(null);
                attachment.setIv(null);
                attachment.setShredded(true);
            }
        }

        // Step 2: Crypto-shred — destroy key in Vault KMS & invalidate metadata
        if (visit.getEncryptionKey() != null) {
            destroyKeyInVault(visit.getEncryptionKey(), timestamp);
        }

        patientVisitRepository.save(visit);

        // Step 3: Evict from Redis cache
        patientVisitCacheService.evict(visit.getId());
    }

    private void destroyKeyInVault(EncryptionKey key, LocalDateTime timestamp) {
        if (key.isInvalidated()) {
            log.info("Encryption key {} is already invalidated and destroyed", key.getKeyId());
            return;
        }
        if (key.getVaultKeyName() != null) {
            // Allow exceptions to propagate — @Transactional caller will roll back the entire
            // erasure if Vault key destruction fails. A deletion proof must not be issued
            // unless the key is actually destroyed in Vault.
            vaultKmsService.destroyKey(key.getVaultKeyName());
            log.info("Vault KEK {} successfully destroyed", key.getVaultKeyName());
        }
        key.setWrappedDek(null);
        key.setKeyValue(null);
        key.setInvalidated(true);
        key.setInvalidatedAt(timestamp);
        encryptionKeyRepository.save(key);
    }

    public ProofVerificationResponseDto verifyProofArtifact(VerifiableDeletionProofDto proof) {
        if (proof == null) {
            return ProofVerificationResponseDto.builder()
                    .valid(false)
                    .verificationMessage("Proof artifact is null")
                    .verifiedAt(LocalDateTime.now())
                    .build();
        }

        // 1. Check SHA-256 payload integrity
        String expectedHash = sha256Hex(proof.getAuditTrail());
        boolean payloadIntegrityValid = expectedHash.equalsIgnoreCase(proof.getAuditTrailHash());

        // 2. Check RSA Digital Signature
        // For visit deletion proofs, the identifier signed is the visit UUID.
        // For full patient deletion proofs, the identifier signed is the patient ID string.
        String identifier;
        if ("VISIT_DELETED".equalsIgnoreCase(proof.getStatus()) || (proof.getVisitId() != null && !"PATIENT_DELETED".equalsIgnoreCase(proof.getStatus()))) {
            identifier = proof.getVisitId() != null ? proof.getVisitId().toString() : (proof.getPatientRecordId() != null ? proof.getPatientRecordId().toString() : proof.getPatientId());
        } else {
            identifier = proof.getPatientId() != null ? proof.getPatientId() : (proof.getVisitId() != null ? proof.getVisitId().toString() : (proof.getPatientRecordId() != null ? proof.getPatientRecordId().toString() : ""));
        }

        String canonicalPayload = buildCanonicalSignPayload(identifier, proof.getTimestamp(), proof.getAuditTrailHash(), proof.getMerkleRoot());
        boolean signatureValid = proofSigningService.verify(canonicalPayload, proof.getDigitalSignature());

        // 3. Check Merkle inclusion if root and path provided
        boolean merkleValid = true;
        if (proof.getMerkleRoot() != null && proof.getMerklePath() != null) {
            merkleValid = merkleTreeService.verifyInclusion(proof.getAuditTrailHash(), proof.getMerklePath(), proof.getMerkleRoot());
        }

        boolean overallValid = payloadIntegrityValid && signatureValid && merkleValid;

        String message;
        if (!payloadIntegrityValid) {
            message = "FAILED: Audit trail payload has been tampered with (SHA-256 hash mismatch).";
        } else if (!signatureValid) {
            message = "FAILED: Digital signature is invalid or forged.";
        } else if (!merkleValid) {
            message = "FAILED: Merkle tree inclusion path verification failed.";
        } else {
            message = "SUCCESS: Deletion proof artifact is valid, untampered, and signed by system authority.";
        }

        return ProofVerificationResponseDto.builder()
                .valid(overallValid)
                .payloadIntegrityValid(payloadIntegrityValid)
                .signatureValid(signatureValid)
                .merkleInclusionValid(merkleValid)
                .verificationMessage(message)
                .verifiedAt(LocalDateTime.now())
                .verifiedByAlgorithm(proof.getSignatureAlgorithm() != null ? proof.getSignatureAlgorithm() : "SHA256withRSA")
                .build();
    }

    private String buildCanonicalSignPayload(String identifier, LocalDateTime timestamp, String sha256Hash, String merkleRoot) {
        return String.format("IDENTIFIER=%s|TIMESTAMP=%s|HASH=%s|MERKLE_ROOT=%s",
                identifier, timestamp, sha256Hash, merkleRoot);
    }

    private String buildPatientAuditTrail(String patientId, int visitsShredded, String requestedBy, LocalDateTime timestamp) {
        return String.format("ACTION=CRYPTO_SHRED_PATIENT|PATIENT_ID=%s|VISITS_COUNT=%d|REQUESTED_BY=%s|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=%s",
                patientId, visitsShredded, requestedBy, timestamp);
    }

    private String buildVisitAuditTrail(UUID visitId, String requestedBy, LocalDateTime timestamp) {
        return String.format("ACTION=CRYPTO_SHRED_VISIT|VISIT_ID=%s|REQUESTED_BY=%s|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=%s",
                visitId, requestedBy, timestamp);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
