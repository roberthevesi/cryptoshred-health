package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.PatientRequest;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatientDemographicEncryptionTest {

    private PatientRepository patientRepository;
    private com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository patientVisitRepository;
    private GpRepository gpRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private EventLogPublisher eventLogPublisher;
    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientRepository = Mockito.mock(PatientRepository.class);
        gpRepository = Mockito.mock(GpRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();
        eventLogPublisher = Mockito.mock(EventLogPublisher.class);

        PatientCacheService patientCacheService = Mockito.mock(PatientCacheService.class);
        patientService = new PatientService(
                patientRepository,
                gpRepository,
                vaultKmsService,
                envelopeEncryptionService,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                patientCacheService,
                eventLogPublisher
        );

        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> {
            Patient p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
    }

    @Test
    void testPatientCreationGeneratesVaultKekAndEncryptedDataBlob() {
        PatientRequest request = new PatientRequest();
        request.setFirstName("Eleanor");
        request.setLastName("Vance");
        request.setDateOfBirth("1988-04-12");
        request.setGender("Female");
        request.setEmail("eleanor.vance@example.com");
        request.setPhoneNumber("+44 7700 900077");
        request.setAddress("42 Hill House Lane, London");
        request.setNhsNumber("943 476 5919");

        final byte[][] capturedDek = new byte[1][];
        when(vaultKmsService.wrapDek(anyString(), any())).thenAnswer(invocation -> {
            capturedDek[0] = invocation.getArgument(1);
            return "wrapped_dek_sample_123";
        });
        when(vaultKmsService.unwrapDek(anyString(), anyString())).thenAnswer(invocation -> capturedDek[0]);

        PatientResponse response = patientService.create(request);

        assertNotNull(response);
        assertEquals("Eleanor", response.getFirstName());
        assertEquals("Vance", response.getLastName());
        assertEquals("eleanor.vance@example.com", response.getEmail());
        assertTrue(response.isActive());
        assertFalse(response.isShredded());

        verify(vaultKmsService, times(1)).wrapDek(startsWith("patient_"), any());
        verify(patientRepository, times(1)).save(any(Patient.class));
        verify(eventLogPublisher, times(1)).publishEvent(argThat(event ->
                "PATIENT_CREATED".equals(event.getEventType()) &&
                response.getPatientId().equals(event.getPatientId()) &&
                event.getVaultKeyName() != null &&
                event.getWrappedDek() != null &&
                event.getEncryptedDataBlob() != null
        ));
    }

    @Test
    void testPatientUpdateEmitsPatientUpdatedEvent() {
        String patientId = "PAT-12345";
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        patient.setFirstName("OldFirst");
        patient.setLastName("OldLast");
        com.roberthevesi.cryptoshred_health.model.EncryptionKey key =
                new com.roberthevesi.cryptoshred_health.model.EncryptionKey(
                        UUID.randomUUID().toString(),
                        "patient_uuid_123",
                        "wrapped_dek_123",
                        "iv_123"
                );
        patient.setEncryptionKey(key);

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] rawDek = envelopeEncryptionService.generateDek();
        when(vaultKmsService.unwrapDek(eq("patient_uuid_123"), eq("wrapped_dek_123"))).thenReturn(rawDek);

        PatientRequest updateReq = new PatientRequest();
        updateReq.setFirstName("NewFirst");
        updateReq.setLastName("NewLast");
        updateReq.setEmail("new.email@example.com");

        PatientResponse updatedResp = patientService.update(patientId, updateReq);

        assertEquals("NewFirst", updatedResp.getFirstName());
        verify(eventLogPublisher, times(1)).publishEvent(argThat(event ->
                "PATIENT_UPDATED".equals(event.getEventType()) &&
                patientId.equals(event.getPatientId()) &&
                "patient_uuid_123".equals(event.getVaultKeyName()) &&
                event.getEncryptedDataBlob() != null
        ));
    }

    @Test
    void testPatientDeactivateEmitsPatientDeactivatedEvent() {
        String patientId = "PAT-54321";
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        patient.setActive(true);

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        patientService.deactivate(patientId);

        assertFalse(patient.isActive());
        verify(eventLogPublisher, times(1)).publishEvent(argThat(event ->
                "PATIENT_DEACTIVATED".equals(event.getEventType()) &&
                patientId.equals(event.getPatientId())
        ));
    }

    @Test
    void testShreddedPatientDemographicsAreRedacted() {
        PatientRequest request = new PatientRequest();
        request.setFirstName("Thomas");
        request.setLastName("Shelby");
        request.setEmail("thomas.shelby@peaky.co.uk");

        final byte[][] capturedDek = new byte[1][];
        when(vaultKmsService.wrapDek(anyString(), any())).thenAnswer(invocation -> {
            capturedDek[0] = invocation.getArgument(1);
            return "wrapped_dek_thomas";
        });
        when(vaultKmsService.unwrapDek(anyString(), anyString())).thenAnswer(invocation -> capturedDek[0]);

        PatientResponse response = patientService.create(request);
        assertEquals("Thomas", response.getFirstName());

        // Simulate Vault KEK destruction (Crypto-Shredding)
        when(vaultKmsService.unwrapDek(anyString(), anyString()))
                .thenThrow(new RuntimeException("Vault key destroyed or inaccessible"));

        // Simulate retrieving the patient entity post-shred
        Patient patient = new Patient();
        patient.setPatientId(response.getPatientId());
        patient.setEncryptedDataBlob("some_encrypted_blob");
        patient.setShredded(true);

        PatientResponse postShredResponse = patientService.toResponse(patient);

        assertTrue(postShredResponse.isShredded());
        assertEquals("[SHREDDED]", postShredResponse.getFirstName());
        assertEquals("[SHREDDED]", postShredResponse.getLastName());
        assertNull(postShredResponse.getEmail());
    }
}
