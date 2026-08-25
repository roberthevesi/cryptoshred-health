package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ObjectMapper objectMapper;
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
        objectMapper = new ObjectMapper();

        patientVisitService = new PatientVisitService(
                patientVisitRepository,
                patientRepository,
                userRepository,
                vaultKmsService,
                envelopeEncryptionService,
                eventLogPublisher,
                patientVisitCacheService,
                objectMapper
        );

        testDoctor = new User();
        testDoctor.setId(UUID.randomUUID());
        testDoctor.setEmail("doctor@hospital.org");
        testDoctor.setRole(Role.DOCTOR);

        when(userRepository.findByEmail("doctor@hospital.org")).thenReturn(Optional.of(testDoctor));
    }

    @Test
    void testRedisCacheHitReturnsDirectlyWithoutSynchronousVaultUnwrap() {
        // Arrange
        UUID visitId = UUID.randomUUID();
        String vaultKeyName = "patient_d3b07384-d113-4673-9080-87a41ec62762_visit_" + visitId;
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
                .medicalNotes("Cached Diagnosis")
                .shredded(false)
                .build();

        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        when(patientVisitCacheService.get(visitId)).thenReturn(cachedResponse);

        // Act
        PatientVisitResponse response = patientVisitService.findById(visitId, "doctor@hospital.org");

        // Assert
        assertNotNull(response);
        assertFalse(response.isShredded());
        assertEquals("Cached Diagnosis", response.getMedicalNotes());
        verify(patientVisitCacheService, times(1)).get(visitId);
        verify(vaultKmsService, never()).unwrapDek(anyString(), anyString());
    }

    @Test
    void testCacheMissDecodesAndCachesResponse() {
        // Arrange
        UUID visitId = UUID.randomUUID();
        String vaultKeyName = "patient_shredded-patient-uuid_visit_" + visitId;
        String wrappedDek = "wrapped_dek_base64";

        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatientName("Jane Doe");
        visit.setOwner(testDoctor);
        EncryptionKey key = new EncryptionKey("keyId", vaultKeyName, wrappedDek, "iv_base64");
        visit.setEncryptionKey(key);

        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        when(patientVisitCacheService.get(visitId)).thenReturn(null);

        // Act
        PatientVisitResponse response = patientVisitService.findById(visitId, "doctor@hospital.org");

        // Assert
        assertNotNull(response);
        verify(patientVisitCacheService, times(1)).get(visitId);
        verify(patientVisitCacheService, times(1)).put(eq(visitId), any(PatientVisitResponse.class));
    }

    @Test
    void testCacheMissWhenVaultKekDestroyedYieldsShreddedResponse() {
        // Arrange
        UUID visitId = UUID.randomUUID();
        String vaultKeyName = "patient_shredded-patient-uuid_visit_" + visitId;
        String wrappedDek = "wrapped_dek_base64";

        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatientName("Jane Doe");
        visit.setOwner(testDoctor);
        visit.setEncryptedDataBlob("ciphertext_blob");
        EncryptionKey key = new EncryptionKey("keyId", vaultKeyName, wrappedDek, "iv_base64");
        visit.setEncryptionKey(key);

        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        when(patientVisitCacheService.get(visitId)).thenReturn(null);

        // Simulate HashiCorp Vault throwing exception because KEK was destroyed in KMS
        when(vaultKmsService.unwrapDek(eq(vaultKeyName), any()))
                .thenThrow(new RuntimeException("Vault key invalid or destroyed: " + vaultKeyName));

        // Act
        PatientVisitResponse response = patientVisitService.findById(visitId, "doctor@hospital.org");

        // Assert
        assertNotNull(response);
        assertTrue(response.isShredded());
        assertEquals("[SHREDDED]", response.getMedicalNotes());
        assertEquals("[SHREDDED]", response.getDiagnosis());
        assertNull(response.getEncryptedDataBlob());
    }
}
