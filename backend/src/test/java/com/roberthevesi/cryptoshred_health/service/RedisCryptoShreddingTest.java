package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.PatientRecordResponse;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientRecord;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientRecordRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RedisCryptoShreddingTest {

    private PatientRecordRepository patientRecordRepository;
    private UserRepository userRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private EventLogPublisher eventLogPublisher;
    private PatientRecordCacheService patientRecordCacheService;
    private PatientRecordService patientRecordService;

    private User testDoctor;

    @BeforeEach
    void setUp() {
        patientRecordRepository = Mockito.mock(PatientRecordRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();
        eventLogPublisher = Mockito.mock(EventLogPublisher.class);
        patientRecordCacheService = Mockito.mock(PatientRecordCacheService.class);

        patientRecordService = new PatientRecordService(
                patientRecordRepository,
                userRepository,
                vaultKmsService,
                envelopeEncryptionService,
                eventLogPublisher,
                patientRecordCacheService
        );

        testDoctor = new User();
        testDoctor.setId(UUID.randomUUID());
        testDoctor.setEmail("doctor@hospital.org");
        testDoctor.setRole(Role.DOCTOR);

        when(userRepository.findByEmail("doctor@hospital.org")).thenReturn(Optional.of(testDoctor));
    }

    @Test
    void testRedisCacheHitWithValidVaultKekSucceeds() {
        // Arrange
        UUID recordId = UUID.randomUUID();
        String vaultKeyName = "patient_kek_active123";
        String wrappedDek = "wrapped_dek_base64";

        PatientRecord record = new PatientRecord();
        record.setId(recordId);
        record.setPatientName("John Doe");
        record.setOwner(testDoctor);
        EncryptionKey key = new EncryptionKey("keyId", vaultKeyName, wrappedDek, "iv_base64");
        record.setEncryptionKey(key);

        PatientRecordResponse cachedResponse = PatientRecordResponse.builder()
                .id(recordId)
                .patientName("John Doe")
                .medicalNotes("Top Secret Diagnosis")
                .encryptedDataBlob("encrypted_blob_base64")
                .shredded(false)
                .build();

        when(patientRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(patientRecordCacheService.get(recordId)).thenReturn(cachedResponse);
        when(vaultKmsService.unwrapDek(vaultKeyName, wrappedDek)).thenReturn(new byte[32]);

        // Act
        PatientRecordResponse response = patientRecordService.findById(recordId, "doctor@hospital.org");

        // Assert
        assertNotNull(response);
        assertFalse(response.isShredded());
        assertEquals("Top Secret Diagnosis", response.getMedicalNotes());
        verify(patientRecordCacheService, times(1)).get(recordId);
        verify(vaultKmsService, times(1)).unwrapDek(vaultKeyName, wrappedDek);
    }

    @Test
    void testStaleRedisCacheHitTriggersZeroPurgeCryptographicInvalidationWhenVaultKekDestroyed() {
        // Arrange — Simulate a stale record left in Redis memory post-deletion
        UUID recordId = UUID.randomUUID();
        String vaultKeyName = "patient_kek_shredded999";
        String wrappedDek = "wrapped_dek_base64";

        PatientRecord record = new PatientRecord();
        record.setId(recordId);
        record.setPatientName("Jane Doe");
        record.setOwner(testDoctor);
        EncryptionKey key = new EncryptionKey("keyId", vaultKeyName, wrappedDek, "iv_base64");
        record.setEncryptionKey(key);

        PatientRecordResponse staleCachedResponse = PatientRecordResponse.builder()
                .id(recordId)
                .patientName("Jane Doe")
                .medicalNotes("Confidential Patient Notes")
                .encryptedDataBlob("encrypted_blob_base64")
                .shredded(false)
                .build();

        when(patientRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(patientRecordCacheService.get(recordId)).thenReturn(staleCachedResponse);

        // Simulate HashiCorp Vault throwing exception because KEK was destroyed in KMS
        when(vaultKmsService.unwrapDek(eq(vaultKeyName), any()))
                .thenThrow(new RuntimeException("Vault key invalid or destroyed: " + vaultKeyName));

        // Act
        PatientRecordResponse response = patientRecordService.findById(recordId, "doctor@hospital.org");

        // Assert — Even though stale response was in Redis RAM, reading it yields [SHREDDED]
        assertNotNull(response);
        assertTrue(response.isShredded());
        assertEquals("[SHREDDED]", response.getMedicalNotes());
        assertEquals("[SHREDDED]", response.getDiagnosis());
        assertNull(response.getEncryptedDataBlob());

        // Verify key was proactively evicted from Redis as a result
        verify(patientRecordCacheService, times(1)).evict(recordId);
    }
}
