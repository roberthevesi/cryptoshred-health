package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
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

    private PatientVisitRepository patientVisitRepository;
    private PatientRepository patientRepository;
    private UserRepository userRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private EventLogPublisher eventLogPublisher;
    private PatientVisitCacheService patientVisitCacheService;
    private PatientVisitService patientVisitService;

    private User testDoctor;

    @BeforeEach
    void setUp() {
        patientVisitRepository = Mockito.mock(PatientVisitRepository.class);
        patientRepository = Mockito.mock(PatientRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();
        eventLogPublisher = Mockito.mock(EventLogPublisher.class);
        patientVisitCacheService = Mockito.mock(PatientVisitCacheService.class);

        patientVisitService = new PatientVisitService(
                patientVisitRepository,
                patientRepository,
                userRepository,
                vaultKmsService,
                envelopeEncryptionService,
                eventLogPublisher,
                patientVisitCacheService
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
        UUID visitId = UUID.randomUUID();
        String vaultKeyName = "patients/d3b07384-d113-4673-9080-87a41ec62762/visits/" + visitId;
        String wrappedDek = "wrapped_dek_base64";

        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatientName("John Doe");
        visit.setOwner(testDoctor);
        EncryptionKey key = new EncryptionKey("keyId", vaultKeyName, wrappedDek, "iv_base64");
        visit.setEncryptionKey(key);

        PatientVisitResponse cachedResponse = PatientVisitResponse.builder()
                .id(visitId)
                .patientName("John Doe")
                .medicalNotes("Top Secret Diagnosis")
                .encryptedDataBlob("encrypted_blob_base64")
                .shredded(false)
                .build();

        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        when(patientVisitCacheService.get(visitId)).thenReturn(cachedResponse);
        when(vaultKmsService.unwrapDek(vaultKeyName, wrappedDek)).thenReturn(new byte[32]);

        // Act
        PatientVisitResponse response = patientVisitService.findById(visitId, "doctor@hospital.org");

        // Assert
        assertNotNull(response);
        assertFalse(response.isShredded());
        assertEquals("Top Secret Diagnosis", response.getMedicalNotes());
        verify(patientVisitCacheService, times(1)).get(visitId);
        verify(vaultKmsService, times(1)).unwrapDek(vaultKeyName, wrappedDek);
    }

    @Test
    void testStaleRedisCacheHitTriggersZeroPurgeCryptographicInvalidationWhenVaultKekDestroyed() {
        // Arrange — Simulate a stale visit left in Redis memory post-deletion
        UUID visitId = UUID.randomUUID();
        String vaultKeyName = "patients/shredded-patient-uuid/visits/" + visitId;
        String wrappedDek = "wrapped_dek_base64";


        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatientName("Jane Doe");
        visit.setOwner(testDoctor);
        EncryptionKey key = new EncryptionKey("keyId", vaultKeyName, wrappedDek, "iv_base64");
        visit.setEncryptionKey(key);

        PatientVisitResponse staleCachedResponse = PatientVisitResponse.builder()
                .id(visitId)
                .patientName("Jane Doe")
                .medicalNotes("Confidential Patient Notes")
                .encryptedDataBlob("encrypted_blob_base64")
                .shredded(false)
                .build();

        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        when(patientVisitCacheService.get(visitId)).thenReturn(staleCachedResponse);

        // Simulate HashiCorp Vault throwing exception because KEK was destroyed in KMS
        when(vaultKmsService.unwrapDek(eq(vaultKeyName), any()))
                .thenThrow(new RuntimeException("Vault key invalid or destroyed: " + vaultKeyName));

        // Act
        PatientVisitResponse response = patientVisitService.findById(visitId, "doctor@hospital.org");

        // Assert — Even though stale response was in Redis RAM, reading it yields [SHREDDED]
        assertNotNull(response);
        assertTrue(response.isShredded());
        assertEquals("[SHREDDED]", response.getMedicalNotes());
        assertEquals("[SHREDDED]", response.getDiagnosis());
        assertNull(response.getEncryptedDataBlob());

        // Verify key was proactively evicted from Redis as a result
        verify(patientVisitCacheService, times(1)).evict(visitId);
    }
}
