package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.KeyRotationRequestDto;
import com.roberthevesi.cryptoshred_health.dto.KeyRotationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.KeyRotationResponseDto.KeyRotationDetailDto;
import com.roberthevesi.cryptoshred_health.dto.KeyStatusSummaryDto;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * KeyManagementService — orchestrates cryptographic key lifecycle operations,
 * including zero-plaintext DEK re-wrapping and Vault Transit KEK version rotation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyManagementService {

    private final EncryptionKeyRepository encryptionKeyRepository;
    private final PatientRepository patientRepository;
    private final PatientVisitRepository patientVisitRepository;
    private final VaultKmsService vaultKmsService;
    private final EventLogPublisher eventLogPublisher;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CryptoMetricsService cryptoMetricsService;

    /**
     * Executes cryptographic key rotation across the requested scope (ALL, PATIENT, VISIT, or KEY).
     */
    @Transactional
    public KeyRotationResponseDto rotateKeys(KeyRotationRequestDto request) {
        long startTime = System.currentTimeMillis();
        String scope = (request != null && request.getScope() != null && !request.getScope().isBlank())
                ? request.getScope().toUpperCase().trim()
                : "ALL";
        String targetId = request != null ? request.getTargetId() : null;

        List<EncryptionKey> keysToProcess = resolveKeysForScope(scope, targetId);
        List<KeyRotationDetailDto> details = new ArrayList<>();

        int rotatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (EncryptionKey key : keysToProcess) {
            if (key.isInvalidated()) {
                skippedCount++;
                details.add(KeyRotationDetailDto.builder()
                        .keyId(key.getKeyId())
                        .vaultKeyName(key.getVaultKeyName())
                        .status("SKIPPED_INVALIDATED")
                        .previousVersion(key.getKeyVersion())
                        .newVersion(key.getKeyVersion())
                        .message("Key has been crypto-shredded and is permanently invalidated.")
                        .build());
                continue;
            }

            if (key.getVaultKeyName() == null || key.getWrappedDek() == null) {
                skippedCount++;
                details.add(KeyRotationDetailDto.builder()
                        .keyId(key.getKeyId())
                        .vaultKeyName(key.getVaultKeyName())
                        .status("SKIPPED_LEGACY")
                        .previousVersion(key.getKeyVersion())
                        .newVersion(key.getKeyVersion())
                        .message("Key is missing Vault Transit envelope metadata.")
                        .build());
                continue;
            }

            try {
                long keyStartTime = System.nanoTime();
                // 1. Advance KEK version inside Vault Transit
                vaultKmsService.rotateKey(key.getVaultKeyName());

                // 2. Re-wrap DEK under the new KEK version (Zero-Plaintext re-encryption inside Vault)
                String newWrappedDek = vaultKmsService.rewrapDek(key.getVaultKeyName(), key.getWrappedDek());

                if (cryptoMetricsService != null) {
                    cryptoMetricsService.recordCryptoDuration("rotate", System.nanoTime() - keyStartTime);
                }

                // 3. Update persistent key entity
                int prevVersion = key.getKeyVersion();
                int newVersion = prevVersion + 1;
                key.setKeyVersion(newVersion);
                key.setWrappedDek(newWrappedDek);
                key.setRotatedAt(LocalDateTime.now());
                encryptionKeyRepository.save(key);

                // 4. Emit KEY_ROTATED / PATIENT_KEY_ROTATED event to Kafka audit log
                String eventType = "KEY_ROTATED";
                String patientId = null;
                UUID visitId = null;

                if (key.getVaultKeyName() != null) {
                    if (key.getVaultKeyName().startsWith("patient_") && !key.getVaultKeyName().contains("_visit_")) {
                        eventType = "PATIENT_KEY_ROTATED";
                        Optional<Patient> pOpt = patientRepository.findByEncryptionKey(key);
                        if (pOpt.isPresent()) {
                            patientId = pOpt.get().getPatientId();
                        } else {
                            try {
                                UUID pUuid = UUID.fromString(key.getVaultKeyName().substring("patient_".length()));
                                patientId = patientRepository.findById(pUuid).map(Patient::getPatientId).orElse(null);
                            } catch (Exception ignored) {}
                        }
                        if (patientId == null && "PATIENT".equalsIgnoreCase(scope) && targetId != null) {
                            patientId = targetId;
                        }
                    } else if (key.getVaultKeyName().contains("_visit_")) {
                        Optional<PatientVisit> vOpt = patientVisitRepository.findByEncryptionKey(key);
                        if (vOpt.isPresent()) {
                            PatientVisit v = vOpt.get();
                            visitId = v.getId();
                            patientId = v.getPatient() != null ? v.getPatient().getPatientId() : v.getMrn();
                        } else {
                            try {
                                int visitIdx = key.getVaultKeyName().indexOf("_visit_");
                                UUID vUuid = UUID.fromString(key.getVaultKeyName().substring(visitIdx + "_visit_".length()));
                                Optional<PatientVisit> vOpt2 = patientVisitRepository.findById(vUuid);
                                if (vOpt2.isPresent()) {
                                    PatientVisit v = vOpt2.get();
                                    visitId = v.getId();
                                    patientId = v.getPatient() != null ? v.getPatient().getPatientId() : v.getMrn();
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                eventLogPublisher.publishEvent(PatientVisitEventDto.builder()
                        .eventId(UUID.randomUUID())
                        .visitId(visitId)
                        .patientId(patientId)
                        .eventType(eventType)
                        .vaultKeyName(key.getVaultKeyName())
                        .wrappedDek(newWrappedDek)
                        .iv(key.getIv())
                        .timestamp(LocalDateTime.now())
                        .build());

                rotatedCount++;
                details.add(KeyRotationDetailDto.builder()
                        .keyId(key.getKeyId())
                        .vaultKeyName(key.getVaultKeyName())
                        .status("ROTATED")
                        .previousVersion(prevVersion)
                        .newVersion(newVersion)
                        .message("Successfully rotated KEK and re-wrapped DEK under version v" + newVersion)
                        .build());

                log.info("Successfully rotated key {} ({}) to version v{}", key.getKeyId(), key.getVaultKeyName(), newVersion);
            } catch (Exception e) {
                failedCount++;
                log.error("Failed to rotate key {} ({}): {}", key.getKeyId(), key.getVaultKeyName(), e.getMessage());
                details.add(KeyRotationDetailDto.builder()
                        .keyId(key.getKeyId())
                        .vaultKeyName(key.getVaultKeyName())
                        .status("FAILED")
                        .previousVersion(key.getKeyVersion())
                        .newVersion(key.getKeyVersion())
                        .message("Rotation failed: " + e.getMessage())
                        .build());
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        String overallStatus = (failedCount > 0) ? (rotatedCount > 0 ? "PARTIAL" : "FAILED") : "SUCCESS";

        return KeyRotationResponseDto.builder()
                .status(overallStatus)
                .scope(scope)
                .totalProcessed(keysToProcess.size())
                .rotatedCount(rotatedCount)
                .skippedCount(skippedCount)
                .durationMs(durationMs)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();
    }

    /**
     * Returns high-level metrics on KMS encryption keys.
     */
    @Transactional(readOnly = true)
    public KeyStatusSummaryDto getKeySummary() {
        long total = encryptionKeyRepository.count();
        long active = encryptionKeyRepository.countByInvalidatedFalse();
        long shredded = encryptionKeyRepository.countByInvalidatedTrue();

        return KeyStatusSummaryDto.builder()
                .totalKeys(total)
                .activeKeys(active)
                .shreddedKeys(shredded)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private List<EncryptionKey> resolveKeysForScope(String scope, String targetId) {
        switch (scope) {
            case "PATIENT":
                if (targetId == null || targetId.isBlank()) {
                    throw new IllegalArgumentException("targetId (patientId) is required for PATIENT scope rotation");
                }
                Patient patient = patientRepository.findByPatientId(targetId)
                        .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + targetId));

                Set<EncryptionKey> patientKeys = new LinkedHashSet<>();
                if (patient.getEncryptionKey() != null) {
                    patientKeys.add(patient.getEncryptionKey());
                }
                List<PatientVisit> visits = patientVisitRepository.findByPatientIdentifier(targetId);
                for (PatientVisit v : visits) {
                    if (v.getEncryptionKey() != null) {
                        patientKeys.add(v.getEncryptionKey());
                    }
                }
                return new ArrayList<>(patientKeys);

            case "VISIT":
                if (targetId == null || targetId.isBlank()) {
                    throw new IllegalArgumentException("targetId (visitId UUID) is required for VISIT scope rotation");
                }
                UUID visitUuid;
                try {
                    visitUuid = UUID.fromString(targetId);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid visitId UUID: " + targetId);
                }
                PatientVisit visit = patientVisitRepository.findById(visitUuid)
                        .orElseThrow(() -> new IllegalArgumentException("Visit not found: " + targetId));

                if (visit.getEncryptionKey() != null) {
                    return List.of(visit.getEncryptionKey());
                }
                return Collections.emptyList();

            case "KEY":
                if (targetId == null || targetId.isBlank()) {
                    throw new IllegalArgumentException("targetId (keyId) is required for KEY scope rotation");
                }
                EncryptionKey key = encryptionKeyRepository.findByKeyId(targetId)
                        .orElseThrow(() -> new IllegalArgumentException("Encryption key not found: " + targetId));
                return List.of(key);

            case "ALL":
            default:
                return encryptionKeyRepository.findAll();
        }
    }
}
