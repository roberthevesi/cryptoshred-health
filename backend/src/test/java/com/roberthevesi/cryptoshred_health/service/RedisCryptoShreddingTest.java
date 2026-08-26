package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.PatientVisitResponse;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.model.Role;
import com.roberthevesi.cryptoshred_health.model.User;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import com.roberthevesi.cryptoshred_health.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
    private GpRepository gpRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private EventLogPublisher eventLogPublisher;
    private PatientVisitCacheService patientVisitCacheService;
    private PatientCacheService patientCacheService;
    private ObjectMapper objectMapper;
    private PatientVisitService patientVisitService;
    private PatientService patientService;

    private User testDoctor;

    @BeforeEach
    void setUp() {
        patientVisitRepository = Mockito.mock(PatientVisitRepository.class);
        patientRepository = Mockito.mock(PatientRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        gpRepository = Mockito.mock(GpRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();
        eventLogPublisher = Mockito.mock(EventLogPublisher.class);
        patientVisitCacheService = Mockito.mock(PatientVisitCacheService.class);
        patientCacheService = Mockito.mock(PatientCacheService.class);
        objectMapper = new ObjectMapper();

        patientVisitService = new PatientVisitService(
                patientVisitRepository,
                patientRepository,
                userRepository,
                vaultKmsService,
                envelopeEncryptionService,
                eventLogPublisher,
                patientVisitCacheService,
                patientCacheService,
                objectMapper
        );

        patientService = new PatientService(
                patientRepository,
                patientVisitRepository,
                gpRepository,
                vaultKmsService,
                envelopeEncryptionService,
                objectMapper,
                patientCacheService,
                eventLogPublisher
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

    @Test
    void testPatientServiceFindAllReturnsCachedPatientDirectlyWithoutVaultUnwrap() {
        // Arrange
        String patientId = "PAT-10001";
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId(patientId);
        patient.setActive(true);
        patient.setEncryptedDataBlob("encrypted_blob_data");
        patient.setEncryptionKey(new EncryptionKey("keyId", "patient_key_name", "wrapped_dek", "iv"));

        PatientResponse cachedResponse = PatientResponse.builder()
                .patientId(patientId)
                .firstName("Eleanor")
                .lastName("Vance")
                .isActive(true)
                .shredded(false)
                .build();

        when(patientRepository.findAll()).thenReturn(List.of(patient));
        when(patientCacheService.get(patientId)).thenReturn(cachedResponse);

        // Act
        List<PatientResponse> responses = patientService.findAll();

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Eleanor", responses.get(0).getFirstName());
        assertEquals(patientId, responses.get(0).getPatientId());
        verify(patientCacheService, times(1)).get(patientId);
        verify(vaultKmsService, never()).unwrapDek(anyString(), anyString());
    }

    @Test
    void testPatientServiceFindAllCacheMissDecryptsAndHydratesCache() throws Exception {
        // Arrange
        String patientId = "PAT-10002";
        UUID patientUuid = UUID.randomUUID();
        String vaultKeyName = "patient_" + patientUuid;
        String wrappedDek = "wrapped_dek_base64";

        byte[] rawDek = envelopeEncryptionService.generateDek();
        Map<String, Object> piiPayload = Map.of(
                "firstName", "Eleanor",
                "lastName", "Vance",
                "gender", "Female",
                "email", "eleanor@example.com"
        );
        String piiJson = objectMapper.writeValueAsString(piiPayload);
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(piiJson.getBytes(StandardCharsets.UTF_8), rawDek);

        Patient patient = new Patient();
        patient.setId(patientUuid);
        patient.setPatientId(patientId);
        patient.setActive(true);
        patient.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
        patient.setEncryptionKey(new EncryptionKey(patientUuid.toString(), vaultKeyName, wrappedDek, encryptedPayload.ivBase64()));

        when(patientRepository.findAll()).thenReturn(List.of(patient));
        when(patientCacheService.get(patientId)).thenReturn(null);
        when(vaultKmsService.unwrapDek(eq(vaultKeyName), eq(wrappedDek))).thenReturn(rawDek);

        // Act
        List<PatientResponse> responses = patientService.findAll();

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Eleanor", responses.get(0).getFirstName());
        assertEquals("Vance", responses.get(0).getLastName());
        assertEquals("eleanor@example.com", responses.get(0).getEmail());
        verify(patientCacheService, times(1)).get(patientId);
        verify(vaultKmsService, times(1)).unwrapDek(eq(vaultKeyName), eq(wrappedDek));
        verify(patientCacheService, times(1)).put(eq(patientId), any(PatientResponse.class));
    }

    @Test
    void testPatientServicePopulatesVisitCountFromRepository() {
        // Arrange
        String patientId = "PAT-10003";
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId(patientId);
        patient.setFirstName("Thomas");
        patient.setLastName("Shelby");
        patient.setActive(true);

        when(patientRepository.findAll()).thenReturn(List.of(patient));
        when(patientCacheService.get(patientId)).thenReturn(null);
        when(patientVisitRepository.countActiveByPatientIdentifier(patientId)).thenReturn(7);

        // Act
        List<PatientResponse> responses = patientService.findAll();

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(7, responses.get(0).getVisitCount());
    }

    @Test
    void testPatientVisitServiceFindByPatientIdentifierUsesCache() {
        // Arrange
        UUID visitId = UUID.randomUUID();
        String patientId = "PAT-10004";
        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setMrn(patientId);
        visit.setPatientName("Arthur Shelby");
        visit.setOwner(testDoctor);

        PatientVisitResponse cached = PatientVisitResponse.builder()
                .id(visitId)
                .patientId(patientId)
                .patientName("Arthur Shelby")
                .diagnosis("Hypertension")
                .build();

        when(patientVisitRepository.findByPatientIdentifier(patientId)).thenReturn(List.of(visit));
        when(patientVisitCacheService.get(visitId)).thenReturn(cached);

        // Act
        List<PatientVisitResponse> responses = patientVisitService.findByPatientIdentifier(patientId, "doctor@hospital.org");

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Hypertension", responses.get(0).getDiagnosis());
        verify(patientVisitCacheService, times(1)).get(visitId);
        verify(vaultKmsService, never()).unwrapDek(anyString(), anyString());
    }
}
