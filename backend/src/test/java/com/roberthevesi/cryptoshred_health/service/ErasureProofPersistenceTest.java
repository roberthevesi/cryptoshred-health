package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.ErasureProofBundleDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ErasureProofPersistenceTest {

    private PatientVisitRepository patientVisitRepository;
    private PatientRepository patientRepository;
    private EncryptionKeyRepository encryptionKeyRepository;
    private VaultKmsService vaultKmsService;
    private EventLogPublisher eventLogPublisher;
    private PatientVisitCacheService patientVisitCacheService;
    private ProofSigningService proofSigningService;
    private MerkleTreeService merkleTreeService;
    private ObjectMapper objectMapper;
    private ErasureService erasureService;

    @BeforeEach
    void setUp() {
        patientVisitRepository = mock(PatientVisitRepository.class);
        patientRepository = mock(PatientRepository.class);
        encryptionKeyRepository = mock(EncryptionKeyRepository.class);
        vaultKmsService = mock(VaultKmsService.class);
        eventLogPublisher = mock(EventLogPublisher.class);
        patientVisitCacheService = mock(PatientVisitCacheService.class);
        PatientCacheService patientCacheService = mock(PatientCacheService.class);
        proofSigningService = mock(ProofSigningService.class);
        merkleTreeService = mock(MerkleTreeService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        when(proofSigningService.sign(anyString())).thenReturn("mock-rsa-signature");
        when(merkleTreeService.getMerkleRoot()).thenReturn("mock-merkle-root");
        when(merkleTreeService.getInclusionProof(anyString())).thenReturn(List.of("proof1", "proof2"));

        erasureService = new ErasureService(
                patientVisitRepository,
                patientRepository,
                encryptionKeyRepository,
                vaultKmsService,
                eventLogPublisher,
                patientVisitCacheService,
                patientCacheService,
                proofSigningService,
                merkleTreeService,
                objectMapper
        );
    }

    @Test
    void testForgetPatientPersistsDeletionProofAndCascadesToVisits() {
        String patientId = "PAT-TEST-001";
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        patient.setFirstName("John");
        patient.setLastName("Doe");
        patient.setShredded(false);
        patient.setActive(true);

        UUID visitId = UUID.randomUUID();
        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatient(patient);
        visit.setMrn(patientId);
        visit.setDiagnosis("Hypertension Consultation");
        visit.setShredded(false);

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(patient));
        when(patientVisitRepository.findByPatientIdentifier(patientId)).thenReturn(List.of(visit));

        VerifiableDeletionProofDto proof = erasureService.forgetPatient(patientId, "auditor_user");

        assertNotNull(proof);
        assertEquals("PATIENT_DELETED", proof.getStatus());
        assertEquals("PATIENT_PROFILE", proof.getScope());
        assertEquals("Patient Demographic Profile: " + patientId, proof.getEntityDescription());
        assertEquals("auditor_user", proof.getRequestedBy());
        assertTrue(patient.isShredded());
        assertFalse(patient.isActive());
        assertNotNull(patient.getDeletionProofJson());
        assertTrue(patient.getDeletionProofJson().contains("PATIENT_DELETED"));

        // Verify cascaded visit proof
        assertTrue(visit.isShredded());
        assertNotNull(visit.getDeletionProofJson());
        assertTrue(visit.getDeletionProofJson().contains("CLINICAL_VISIT"));
        assertTrue(visit.getDeletionProofJson().contains("Hypertension Consultation"));

        verify(patientRepository, atLeastOnce()).save(patient);
        verify(patientVisitRepository, atLeastOnce()).save(visit);

        // Now test bundle retrieval
        ErasureProofBundleDto bundle = erasureService.getPatientDeletionProofBundle(patientId);
        assertNotNull(bundle);
        assertEquals(patientId, bundle.getPatientId());
        assertNotNull(bundle.getMasterPatientProof());
        assertEquals("PATIENT_PROFILE", bundle.getMasterPatientProof().getScope());
        assertEquals(1, bundle.getVisitProofs().size());
        assertEquals("CLINICAL_VISIT", bundle.getVisitProofs().get(0).getScope());
        assertEquals(1, bundle.getTotalShreddedVisits());
    }

    @Test
    void testForgetVisitPersistsDeletionProof() {
        UUID visitId = UUID.randomUUID();
        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setMrn("PAT-TEST-002");
        visit.setPatientName("Jane Doe");
        visit.setDiagnosis("Routine Health Check");
        visit.setShredded(false);

        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));

        VerifiableDeletionProofDto proof = erasureService.forgetVisit(visitId, "auditor_user");

        assertNotNull(proof);
        assertEquals("VISIT_DELETED", proof.getStatus());
        assertEquals("CLINICAL_VISIT", proof.getScope());
        assertTrue(proof.getEntityDescription().contains("Routine Health Check"));
        assertTrue(visit.isShredded());
        assertNotNull(visit.getDeletionProofJson());
        assertTrue(visit.getDeletionProofJson().contains("VISIT_DELETED"));

        verify(patientVisitRepository, atLeastOnce()).save(visit);

        // Now test retrieval
        VerifiableDeletionProofDto retrievedProof = erasureService.getVisitDeletionProof(visitId);
        assertNotNull(retrievedProof);
        assertEquals(visitId, retrievedProof.getVisitId());
        assertEquals("VISIT_DELETED", retrievedProof.getStatus());
        assertEquals("CLINICAL_VISIT", retrievedProof.getScope());
    }

    @Test
    void testGetPatientDeletionProofThrowsWhenNotShredded() {
        String patientId = "PAT-ACTIVE-001";
        Patient patient = new Patient();
        patient.setPatientId(patientId);
        patient.setDeletionProofJson(null);

        when(patientRepository.findByPatientId(patientId)).thenReturn(Optional.of(patient));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                erasureService.getPatientDeletionProof(patientId));

        assertTrue(exception.getMessage().contains("No deletion proof found for patient"));
    }

    @Test
    void testGetVisitDeletionProofThrowsWhenNotShredded() {
        UUID visitId = UUID.randomUUID();
        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setDeletionProofJson(null);

        when(patientVisitRepository.findById(visitId)).thenReturn(Optional.of(visit));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                erasureService.getVisitDeletionProof(visitId));

        assertTrue(exception.getMessage().contains("No deletion proof found for visit"));
    }
}
