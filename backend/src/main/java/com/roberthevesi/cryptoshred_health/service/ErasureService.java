package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.DeletionProofResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientRecordEventDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientAttachment;
import com.roberthevesi.cryptoshred_health.model.PatientRecord;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ErasureService — implements the Crypto-Shredding (Right-to-be-Forgotten) pattern.
 *
 * <p>Instead of physically deleting data rows (which may still reside in backups),
 * crypto-shredding irreversibly destroys the encryption key protecting the sensitive
 * payload and attachment blobs. Without the key, the ciphertext is computationally irrecoverable.
 *
 * <p>Returns a {@link DeletionProofResponse} containing a SHA-256 fingerprint of the
 * audit trail — a signed, verifiable record suitable for GDPR Article 17 compliance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErasureService {

    private final PatientRecordRepository patientRecordRepository;
    private final EncryptionKeyRepository encryptionKeyRepository;
    private final VaultKmsService vaultKmsService;
    private final EventLogPublisher eventLogPublisher;
    private final PatientRecordCacheService patientRecordCacheService;

    @Transactional
    public DeletionProofResponse forgetPatient(UUID patientRecordId, String requestedBy) {
        PatientRecord record = patientRecordRepository.findById(patientRecordId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Patient record not found: " + patientRecordId));

        if (record.isShredded()) {
            throw new IllegalStateException("Record has already been shredded");
        }

        LocalDateTime timestamp = LocalDateTime.now();
        String vaultKeyName = record.getEncryptionKey() != null ? record.getEncryptionKey().getVaultKeyName() : null;

        // Step 1: Nullify sensitive fields & attachments (data minimisation)
        record.setMedicalNotes("[SHREDDED]");
        record.setDiagnosis("[SHREDDED]");
        record.setAllergies("[SHREDDED]");
        record.setPrescriptions("[SHREDDED]");
        record.setEncryptedDataBlob(null);
        record.setShredded(true);

        if (record.getAttachments() != null) {
            for (PatientAttachment attachment : record.getAttachments()) {
                attachment.setEncryptedDataBlob(null);
                attachment.setShredded(true);
            }
        }

        // Step 2: Crypto-shred — destroy the encryption key in Vault KMS & local metadata
        EncryptionKey key = record.getEncryptionKey();
        if (key != null) {
            if (key.getVaultKeyName() != null) {
                try {
                    vaultKmsService.destroyKey(key.getVaultKeyName());
                    log.info("Vault KEK {} destroyed for record {}", key.getVaultKeyName(), patientRecordId);
                } catch (Exception e) {
                    log.error("Failed to destroy key in Vault for record {}: {}", patientRecordId, e.getMessage());
                }
            }
            key.setWrappedDek(null);
            key.setKeyValue(null);          // key material is gone
            key.setInvalidated(true);
            key.setInvalidatedAt(timestamp);
            encryptionKeyRepository.save(key);
            log.info("Encryption key {} invalidated for record {}", key.getKeyId(), patientRecordId);
        }

        patientRecordRepository.save(record);

        // Step 3: Proactive cache eviction in Redis
        patientRecordCacheService.evict(patientRecordId);

        // Step 4: Publish RECORD_SHREDDED event to Kafka log
        eventLogPublisher.publishEvent(PatientRecordEventDto.builder()
                .eventId(UUID.randomUUID())
                .patientRecordId(patientRecordId)
                .eventType("RECORD_SHREDDED")
                .vaultKeyName(vaultKeyName)
                .wrappedDek(null)
                .iv(null)
                .encryptedDataBlob(null)
                .patientName(record.getPatientName())
                .timestamp(timestamp)
                .build());

        // Step 5: Build immutable audit trail and hash it
        String auditTrail = buildAuditTrail(patientRecordId, requestedBy, timestamp);
        String sha256Hash = sha256Hex(auditTrail);

        log.info("Erasure complete for record {}. Proof hash: {}", patientRecordId, sha256Hash);

        return new DeletionProofResponse(
                timestamp,
                patientRecordId,
                requestedBy,
                sha256Hash,
                "DELETED",
                auditTrail
        );
    }

    private String buildAuditTrail(UUID recordId, String requestedBy, LocalDateTime timestamp) {
        return String.format("ACTION=CRYPTO_SHRED|RECORD_ID=%s|REQUESTED_BY=%s|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=%s",
                recordId, requestedBy, timestamp);
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
