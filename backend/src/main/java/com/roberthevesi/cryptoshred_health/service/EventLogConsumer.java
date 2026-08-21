package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.config.KafkaTopicConfig;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventLogConsumer {

    private final ObjectMapper objectMapper;
    private final VaultKmsService vaultKmsService;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    @Getter
    private final List<PatientVisitEventDto> capturedEvents = Collections.synchronizedList(new ArrayList<>());

    @KafkaListener(topics = KafkaTopicConfig.TOPIC_PATIENT_EVENTS, groupId = "cryptoshred-audit-group")
    public void consumeEvent(String message) {
        try {
            PatientVisitEventDto event = objectMapper.readValue(message, PatientVisitEventDto.class);
            capturedEvents.add(event);
            log.info("Kafka Consumer received event [{}] for patient visit {}",
                    event.getEventType(), event.getVisitId());
        } catch (Exception e) {
            log.error("Failed to parse incoming Kafka event log message: {}", e.getMessage());
        }
    }

    /**
     * Demonstrates attempting decryption of a Kafka event log payload.
     */
    public String attemptDecryptEventPayload(PatientVisitEventDto event) {
        if (event.getVaultKeyName() == null || event.getWrappedDek() == null || event.getEncryptedDataBlob() == null) {
            throw new IllegalArgumentException("Event does not contain envelope encryption payload metadata");
        }

        try {
            // 1. Unwrap DEK from Vault KMS
            byte[] dek = vaultKmsService.unwrapDek(event.getVaultKeyName(), event.getWrappedDek());

            // 2. Decrypt ciphertext payload
            byte[] plaintextBytes = envelopeEncryptionService.decrypt(
                    event.getEncryptedDataBlob(),
                    event.getIv(),
                    dek);

            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Post-shred Kafka event log decryption attempt failed as expected for key {}: {}",
                    event.getVaultKeyName(), e.getMessage());
            throw new IllegalStateException("Kafka event log payload is un-decryptable (Vault KEK destroyed): " + e.getMessage(), e);
        }
    }
}
