package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class KafkaEventLogCryptoShredTest {

    private EnvelopeEncryptionService envelopeEncryptionService;
    private VaultKmsService vaultKmsService;
    private EventLogConsumer eventLogConsumer;

    @BeforeEach
    void setUp() {
        envelopeEncryptionService = new EnvelopeEncryptionService();
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        eventLogConsumer = new EventLogConsumer(new ObjectMapper(), vaultKmsService, envelopeEncryptionService);
    }

    @Test
    void testPreShredKafkaEventDecryptionSucceeds() {
        // Arrange
        byte[] dek = envelopeEncryptionService.generateDek();
        String originalNotes = "Patient presented with acute migraines.";
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(originalNotes.getBytes(StandardCharsets.UTF_8), dek);

        String vaultKeyName = "patients/d3b07384-d113-4673-9080-87a41ec62762/visits/878a6e3b-5b44-4fc2-99ce-97894ee40a2d";
        String wrappedDek = "wrapped_dek_base64_sample";

        when(vaultKmsService.unwrapDek(vaultKeyName, wrappedDek)).thenReturn(dek);

        PatientVisitEventDto event = PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .visitId(UUID.randomUUID())
                .patientId("PAT-10001")
                .eventType("VISIT_CREATED")
                .vaultKeyName(vaultKeyName)
                .wrappedDek(wrappedDek)
                .iv(encryptedPayload.ivBase64())
                .encryptedDataBlob(encryptedPayload.ciphertextBase64())
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        String decryptedText = eventLogConsumer.attemptDecryptEventPayload(event);

        // Assert
        assertEquals(originalNotes, decryptedText);
    }

    @Test
    void testPostShredKafkaEventDecryptionFailsWhenKekDestroyed() {
        // Arrange
        byte[] dek = envelopeEncryptionService.generateDek();
        String originalNotes = "Confidential psychiatric evaluation notes.";
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(originalNotes.getBytes(StandardCharsets.UTF_8), dek);

        String vaultKeyName = "patients/shredded-patient/visits/shredded-visit";
        String wrappedDek = "wrapped_dek_base64_sample";


        // Simulate Vault returning error because key was deleted
        when(vaultKmsService.unwrapDek(anyString(), anyString()))
                .thenThrow(new RuntimeException("Vault key invalid or destroyed"));

        PatientVisitEventDto event = PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .visitId(UUID.randomUUID())
                .patientId("PAT-99999")
                .eventType("VISIT_CREATED")
                .vaultKeyName(vaultKeyName)
                .wrappedDek(wrappedDek)
                .iv(encryptedPayload.ivBase64())
                .encryptedDataBlob(encryptedPayload.ciphertextBase64())
                .timestamp(LocalDateTime.now())
                .build();

        // Act & Assert: Attempting post-shred decryption of immutable Kafka event log fails
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> eventLogConsumer.attemptDecryptEventPayload(event)
        );

        assertTrue(ex.getMessage().contains("Vault KEK destroyed"));
    }
}
