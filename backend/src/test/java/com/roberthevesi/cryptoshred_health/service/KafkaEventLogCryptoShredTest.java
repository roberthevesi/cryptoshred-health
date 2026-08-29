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
import static org.mockito.ArgumentMatchers.eq;
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
        UUID visitId = UUID.randomUUID();
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(originalNotes.getBytes(StandardCharsets.UTF_8), dek, visitId.toString().getBytes(StandardCharsets.UTF_8));

        String vaultKeyName = "patient_d3b07384-d113-4673-9080-87a41ec62762_visit_878a6e3b-5b44-4fc2-99ce-97894ee40a2d";
        String wrappedDek = "wrapped_dek_base64_sample";

        when(vaultKmsService.unwrapDek(vaultKeyName, wrappedDek)).thenReturn(dek);

        PatientVisitEventDto event = PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .visitId(visitId)
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
        UUID visitId = UUID.randomUUID();
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(originalNotes.getBytes(StandardCharsets.UTF_8), dek, visitId.toString().getBytes(StandardCharsets.UTF_8));

        String vaultKeyName = "patient_shredded-patient_visit_shredded-visit";
        String wrappedDek = "wrapped_dek_base64_sample";



        // Simulate Vault returning error because key was deleted
        when(vaultKmsService.unwrapDek(anyString(), anyString()))
                .thenThrow(new RuntimeException("Vault key invalid or destroyed"));

        PatientVisitEventDto event = PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .visitId(visitId)
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

    @Test
    void testPatientCreatedKafkaEventDecryptionSucceedsWhenActive() {
        // Arrange
        byte[] dek = envelopeEncryptionService.generateDek();
        String demographicPiiJson = "{\"firstName\":\"Arthur\",\"lastName\":\"Dent\",\"email\":\"arthur@galaxy.com\",\"nhsNumber\":\"999-000-111\"}";
        String patientId = "PAT-42424";
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(demographicPiiJson.getBytes(StandardCharsets.UTF_8), dek, patientId.getBytes(StandardCharsets.UTF_8));

        String vaultKeyName = "patient_a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String wrappedDek = "wrapped_dek_arthur_dent";

        when(vaultKmsService.unwrapDek(vaultKeyName, wrappedDek)).thenReturn(dek);

        PatientVisitEventDto event = PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .patientId(patientId)
                .eventType("PATIENT_CREATED")
                .vaultKeyName(vaultKeyName)
                .wrappedDek(wrappedDek)
                .iv(encryptedPayload.ivBase64())
                .encryptedDataBlob(encryptedPayload.ciphertextBase64())
                .timestamp(LocalDateTime.now())
                .build();

        // Act
        String decryptedJson = eventLogConsumer.attemptDecryptEventPayload(event);

        // Assert
        assertEquals(demographicPiiJson, decryptedJson);
        assertTrue(decryptedJson.contains("Arthur"));
        assertTrue(decryptedJson.contains("arthur@galaxy.com"));
    }

    @Test
    void testPatientCreatedKafkaEventDecryptionFailsPostCryptoShred() {
        // Arrange
        byte[] dek = envelopeEncryptionService.generateDek();
        String demographicPiiJson = "{\"firstName\":\"Ford\",\"lastName\":\"Prefect\",\"email\":\"ford@galaxy.com\"}";
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(demographicPiiJson.getBytes(StandardCharsets.UTF_8), dek);

        String vaultKeyName = "patient_shredded-patient-uuid";
        String wrappedDek = "wrapped_dek_ford";

        // Simulate destroyed Vault KEK
        when(vaultKmsService.unwrapDek(eq(vaultKeyName), anyString()))
                .thenThrow(new RuntimeException("Vault key invalid or destroyed"));

        PatientVisitEventDto event = PatientVisitEventDto.builder()
                .eventId(UUID.randomUUID())
                .patientId("PAT-77777")
                .eventType("PATIENT_CREATED")
                .vaultKeyName(vaultKeyName)
                .wrappedDek(wrappedDek)
                .iv(encryptedPayload.ivBase64())
                .encryptedDataBlob(encryptedPayload.ciphertextBase64())
                .timestamp(LocalDateTime.now())
                .build();

        // Act & Assert
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> eventLogConsumer.attemptDecryptEventPayload(event)
        );

        assertTrue(ex.getMessage().contains("Vault KEK destroyed"));
    }
}
