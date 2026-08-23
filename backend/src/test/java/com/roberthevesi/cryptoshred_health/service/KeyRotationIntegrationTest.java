package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.KeyRotationRequestDto;
import com.roberthevesi.cryptoshred_health.dto.KeyRotationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.KeyStatusSummaryDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KeyRotationIntegrationTest {

    private EncryptionKeyRepository encryptionKeyRepository;
    private PatientRepository patientRepository;
    private PatientVisitRepository patientVisitRepository;
    private VaultKmsService vaultKmsService;
    private EventLogPublisher eventLogPublisher;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private KeyManagementService keyManagementService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        encryptionKeyRepository = Mockito.mock(EncryptionKeyRepository.class);
        patientRepository = Mockito.mock(PatientRepository.class);
        patientVisitRepository = Mockito.mock(PatientVisitRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        eventLogPublisher = Mockito.mock(EventLogPublisher.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();

        keyManagementService = new KeyManagementService(
                encryptionKeyRepository,
                patientRepository,
                patientVisitRepository,
                vaultKmsService,
                eventLogPublisher
        );
    }

    @Test
    @DisplayName("Should successfully rotate KEK and rewrap DEK under new key version (v1 -> v2) with zero plaintext exposure")
    void testSuccessfulKeyRotationAndDecryptionContinuity() throws Exception {
        // 1. Prepare raw DEK and encrypted payload
        byte[] rawDek = envelopeEncryptionService.generateDek();
        Map<String, Object> payload = Map.of("diagnosis", "Hypertension Stage 2", "notes", "Prescribed Amlodipine 5mg");
        String plaintextJson = objectMapper.writeValueAsString(payload);

        EnvelopeEncryptionService.EncryptedPayload encrypted =
                envelopeEncryptionService.encrypt(plaintextJson.getBytes(StandardCharsets.UTF_8), rawDek);

        // 2. Prepare EncryptionKey entity at version 1
        String patientUuid = "d3b07384-d113-4673-9080-87a41ec62762";
        String keyId = patientUuid;
        String vaultKeyName = "patient_" + patientUuid;
        String initialWrappedDek = "vault:v1:InitialWrappedBase64Ciphertext";

        EncryptionKey key = new EncryptionKey(keyId, vaultKeyName, initialWrappedDek, encrypted.ivBase64());
        key.setKeyVersion(1);

        when(encryptionKeyRepository.findAll()).thenReturn(List.of(key));
        when(encryptionKeyRepository.save(any(EncryptionKey.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock Vault Transit rotation and re-wrapping
        doNothing().when(vaultKmsService).rotateKey(eq(vaultKeyName));
        when(vaultKmsService.rewrapDek(eq(vaultKeyName), eq(initialWrappedDek)))
                .thenReturn("vault:v2:RewrappedBase64CiphertextUnderVersion2");

        // Mock unwrap capability for both versions
        when(vaultKmsService.unwrapDek(eq(vaultKeyName), anyString())).thenReturn(rawDek);

        // 3. Execute bulk key rotation
        KeyRotationRequestDto request = KeyRotationRequestDto.builder().scope("ALL").build();
        KeyRotationResponseDto response = keyManagementService.rotateKeys(request);

        // 4. Assert rotation response
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getTotalProcessed());
        assertEquals(1, response.getRotatedCount());
        assertEquals(0, response.getSkippedCount());

        // Verify entity state transition
        assertEquals(2, key.getKeyVersion());
        assertEquals("vault:v2:RewrappedBase64CiphertextUnderVersion2", key.getWrappedDek());
        assertNotNull(key.getRotatedAt());

        // 5. Verify cryptographic continuity: decrypt ciphertext using re-wrapped DEK
        byte[] unwrappedDekAfterRotation = vaultKmsService.unwrapDek(key.getVaultKeyName(), key.getWrappedDek());
        byte[] decryptedBytes = envelopeEncryptionService.decrypt(
                encrypted.ciphertextBase64(),
                key.getIv(),
                unwrappedDekAfterRotation);

        String decryptedJson = new String(decryptedBytes, StandardCharsets.UTF_8);
        assertTrue(decryptedJson.contains("Hypertension Stage 2"));
        assertTrue(decryptedJson.contains("Prescribed Amlodipine 5mg"));

        verify(vaultKmsService, times(1)).rotateKey(vaultKeyName);
        verify(vaultKmsService, times(1)).rewrapDek(vaultKeyName, initialWrappedDek);
        verify(eventLogPublisher, times(1)).publishEvent(any());
        verify(encryptionKeyRepository, times(1)).save(key);
    }

    @Test
    @DisplayName("Should skip crypto-shredded keys and prevent re-encryption of invalidated keys")
    void testShreddedKeysAreSkippedDuringRotation() {
        String keyId = "key-shredded-999";
        String vaultKeyName = "patient_shredded-patient-uuid_visit_shredded-visit-uuid";

        EncryptionKey shreddedKey = new EncryptionKey(keyId, vaultKeyName, null, null);
        shreddedKey.setInvalidated(true);
        shreddedKey.setInvalidatedAt(LocalDateTime.now().minusDays(1));
        shreddedKey.setKeyVersion(1);

        when(encryptionKeyRepository.findAll()).thenReturn(List.of(shreddedKey));

        KeyRotationRequestDto request = KeyRotationRequestDto.builder().scope("ALL").build();
        KeyRotationResponseDto response = keyManagementService.rotateKeys(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getTotalProcessed());
        assertEquals(0, response.getRotatedCount());
        assertEquals(1, response.getSkippedCount());
        assertEquals("SKIPPED_INVALIDATED", response.getDetails().get(0).getStatus());

        verify(vaultKmsService, never()).rotateKey(anyString());
        verify(vaultKmsService, never()).rewrapDek(anyString(), anyString());
        verify(encryptionKeyRepository, never()).save(shreddedKey);
    }

    @Test
    @DisplayName("Should rotate only the targeted patient's demographic and visit keys in PATIENT scope")
    void testPatientScopedKeyRotation() {
        String patientId = "PAT-88888";
        UUID patientUuid = UUID.randomUUID();
        UUID visitUuid = UUID.randomUUID();

        String patientVaultKey = "patient_" + patientUuid;
        String visitVaultKey = "patient_" + patientUuid + "_visit_" + visitUuid;


        EncryptionKey demographicKey = new EncryptionKey(patientUuid.toString(), patientVaultKey, "vault:v1:demo", "iv1");
        demographicKey.setKeyVersion(1);

        EncryptionKey visitKey = new EncryptionKey(visitUuid.toString(), visitVaultKey, "vault:v1:visit", "iv2");
        visitKey.setKeyVersion(1);

        Patient patient = new Patient();
        patient.setId(patientUuid);
        patient.setPatientId(patientId);
        patient.setEncryptionKey(demographicKey);

        PatientVisit visit = new PatientVisit();
        visit.setId(visitUuid);
        visit.setPatient(patient);
        visit.setEncryptionKey(visitKey);

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(patient));
        when(patientVisitRepository.findByPatientIdentifier(patientId)).thenReturn(List.of(visit));
        when(encryptionKeyRepository.save(any(EncryptionKey.class))).thenAnswer(inv -> inv.getArgument(0));

        when(vaultKmsService.rewrapDek(eq(patientVaultKey), anyString())).thenReturn("vault:v2:demo_rewrapped");
        when(vaultKmsService.rewrapDek(eq(visitVaultKey), anyString())).thenReturn("vault:v2:visit_rewrapped");

        KeyRotationRequestDto request = KeyRotationRequestDto.builder()
                .scope("PATIENT")
                .targetId(patientId)
                .build();

        KeyRotationResponseDto response = keyManagementService.rotateKeys(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("PATIENT", response.getScope());
        assertEquals(2, response.getTotalProcessed());
        assertEquals(2, response.getRotatedCount());
        assertEquals(0, response.getSkippedCount());

        assertEquals(2, demographicKey.getKeyVersion());
        assertEquals("vault:v2:demo_rewrapped", demographicKey.getWrappedDek());

        assertEquals(2, visitKey.getKeyVersion());
        assertEquals("vault:v2:visit_rewrapped", visitKey.getWrappedDek());

        verify(vaultKmsService, times(1)).rotateKey(patientVaultKey);
        verify(vaultKmsService, times(1)).rotateKey(visitVaultKey);

    }

    @Test
    @DisplayName("Should return accurate key summary metrics")
    void testGetKeySummary() {
        when(encryptionKeyRepository.count()).thenReturn(10L);
        when(encryptionKeyRepository.countByInvalidatedFalse()).thenReturn(8L);
        when(encryptionKeyRepository.countByInvalidatedTrue()).thenReturn(2L);

        KeyStatusSummaryDto summary = keyManagementService.getKeySummary();

        assertNotNull(summary);
        assertEquals(10L, summary.getTotalKeys());
        assertEquals(8L, summary.getActiveKeys());
        assertEquals(2L, summary.getShreddedKeys());
        assertNotNull(summary.getTimestamp());
    }
}
