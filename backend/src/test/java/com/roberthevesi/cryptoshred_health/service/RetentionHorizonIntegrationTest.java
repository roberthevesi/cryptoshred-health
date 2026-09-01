package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.dto.PatientResponse;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.EncryptionKeyRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetentionHorizonIntegrationTest {

    private PatientRepository patientRepository;
    private PatientVisitRepository patientVisitRepository;
    private EncryptionKeyRepository encryptionKeyRepository;
    private VaultKmsService vaultKmsService;
    private EventLogPublisher eventLogPublisher;
    private PatientVisitCacheService patientVisitCacheService;
    private PatientCacheService patientCacheService;
    private ProofSigningService proofSigningService;
    private MerkleTreeService merkleTreeService;
    private ObjectMapper objectMapper;
    private WormBackupExporterService wormBackupExporterService;
    private RetentionPolicyService retentionPolicyService;

    private PatientService patientService;
    private ErasureService erasureService;

    @BeforeEach
    void setUp() {
        patientRepository = mock(PatientRepository.class);
        patientVisitRepository = mock(PatientVisitRepository.class);
        encryptionKeyRepository = mock(EncryptionKeyRepository.class);
        vaultKmsService = mock(VaultKmsService.class);
        eventLogPublisher = mock(EventLogPublisher.class);
        patientVisitCacheService = mock(PatientVisitCacheService.class);
        patientCacheService = mock(PatientCacheService.class);
        proofSigningService = mock(ProofSigningService.class);
        merkleTreeService = mock(MerkleTreeService.class);
        wormBackupExporterService = mock(WormBackupExporterService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        retentionPolicyService = new RetentionPolicyService(8);

        when(proofSigningService.sign(anyString())).thenReturn("mock-rsa-signature");
        when(proofSigningService.signPqc(anyString())).thenReturn("mock-pqc-signature");
        when(merkleTreeService.getMerkleRoot()).thenReturn("mock-merkle-root");
        when(merkleTreeService.getInclusionProof(anyString())).thenReturn(List.of("path1", "path2"));

        patientService = new PatientService(
                patientRepository,
                null,
                null,
                null,
                vaultKmsService,
                new EnvelopeEncryptionService(),
                new CryptoService(),
                objectMapper,
                patientCacheService,
                eventLogPublisher,
                patientVisitRepository,
                retentionPolicyService
        );

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
                objectMapper,
                wormBackupExporterService,
                retentionPolicyService
        );
    }

    @Test
    void testHistoricalPatientCalculatesAsEligible() {
        UUID pId = UUID.randomUUID();
        Patient patient = new Patient();
        patient.setId(pId);
        patient.setPatientId("PAT-10001");
        patient.setFirstName("Historical");
        patient.setLastName("Patient");
        patient.setCreatedAt(LocalDateTime.now().minusYears(12));

        when(patientVisitRepository.findMaxActiveCreatedAtByPatient(pId, "PAT-10001"))
                .thenReturn(LocalDateTime.now().minusYears(10));

        PatientResponse response = patientService.toResponse(patient);
        assertNotNull(response);
        assertEquals("ELIGIBLE", response.getRetentionStatus());
        assertEquals(0L, response.getRetentionDaysRemaining());
        assertEquals(8, response.getRetentionPeriodYears());
        assertTrue(response.getLegalErasureEligibleDate().isBefore(LocalDateTime.now()));
    }

    @Test
    void testRecentPatientCalculatesAsProtected() {
        UUID pId = UUID.randomUUID();
        Patient patient = new Patient();
        patient.setId(pId);
        patient.setPatientId("PAT-10055");
        patient.setFirstName("Recent");
        patient.setLastName("Patient");
        patient.setCreatedAt(LocalDateTime.now().minusYears(2));

        when(patientVisitRepository.findMaxActiveCreatedAtByPatient(pId, "PAT-10055"))
                .thenReturn(LocalDateTime.now().minusMonths(6));

        PatientResponse response = patientService.toResponse(patient);
        assertNotNull(response);
        assertEquals("PROTECTED", response.getRetentionStatus());
        assertTrue(response.getRetentionDaysRemaining() > 0);
        assertEquals(8, response.getRetentionPeriodYears());
        assertTrue(response.getLegalErasureEligibleDate().isAfter(LocalDateTime.now()));
    }

    @Test
    void testDynamicRetentionPolicyUpdateAffectsEligibility() {
        UUID pId = UUID.randomUUID();
        Patient patient = new Patient();
        patient.setId(pId);
        patient.setPatientId("PAT-10020");
        patient.setFirstName("Borderline");
        patient.setLastName("Patient");
        patient.setCreatedAt(LocalDateTime.now().minusYears(7));

        when(patientVisitRepository.findMaxActiveCreatedAtByPatient(pId, "PAT-10020"))
                .thenReturn(LocalDateTime.now().minusYears(7));

        // With 8 years retention (NHS default), 7 years elapsed -> PROTECTED
        PatientResponse resp8 = patientService.toResponse(patient);
        assertEquals("PROTECTED", resp8.getRetentionStatus());

        // Update retention policy to 6 years (HIPAA standard)
        retentionPolicyService.updateRetentionPolicy(6, "admin@cryptoshred.health");

        // Now 7 years elapsed > 6 years retention -> ELIGIBLE
        PatientResponse resp6 = patientService.toResponse(patient);
        assertEquals("ELIGIBLE", resp6.getRetentionStatus());
        assertEquals(0L, resp6.getRetentionDaysRemaining());
    }

    @Test
    void testShreddedNewVisitDoesNotBlockHistoricalPatientErasureEligibility() {
        UUID pId = UUID.randomUUID();
        Patient patient = new Patient();
        patient.setId(pId);
        patient.setPatientId("PAT-10042");
        patient.setFirstName("HistoricalWithCourtShreddedVisit");
        patient.setLastName("Patient");
        patient.setCreatedAt(LocalDateTime.now().minusYears(10)); // Registered 10 years ago

        // Patient had a 10-year-old visit (historical), and a brand-new visit today that was shredded by court order.
        // Active max visit date is the 10-year-old visit.
        when(patientVisitRepository.findMaxActiveCreatedAtByPatient(pId, "PAT-10042"))
                .thenReturn(LocalDateTime.now().minusYears(10));

        PatientResponse response = patientService.toResponse(patient);

        // The patient must remain ELIGIBLE for master erasure because the only active data is 10 years old (> 8 years)
        assertNotNull(response);
        assertEquals("ELIGIBLE", response.getRetentionStatus());
        assertEquals(0L, response.getRetentionDaysRemaining());
        assertTrue(response.getLegalErasureEligibleDate().isBefore(LocalDateTime.now()));
    }

    @Test
    void testCryptoShredPatientBindsRetentionStatusAndOverrideReasonToProof() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setPatientId("PAT-10099");
        patient.setFirstName("Protected");
        patient.setLastName("Patient");
        patient.setCreatedAt(LocalDateTime.now().minusYears(1));
        patient.setShredded(false);

        EncryptionKey key = new EncryptionKey();
        key.setVaultKeyName("cryptoshred-dek-PAT-10099");
        key.setWrappedDek("mock-wrapped-dek");
        patient.setEncryptionKey(key);

        when(patientRepository.findByPatientId("PAT-10099")).thenReturn(Optional.of(patient));
        when(patientVisitRepository.findByPatientIdentifier("PAT-10099")).thenReturn(List.of());

        VerifiableDeletionProofDto proof = erasureService.forgetPatient(
                "PAT-10099",
                "doctor@cryptoshred.health",
                "CONSENT_WITHDRAWN"
        );

        assertNotNull(proof);
        assertEquals("PATIENT_PROFILE", proof.getScope());
        assertEquals("PAT-10099", proof.getPatientId());
        assertEquals("PROTECTED", proof.getRetentionStatus());
        assertEquals("CONSENT_WITHDRAWN", proof.getOverrideReason());
        assertNotNull(proof.getAuditTrail());
        assertTrue(proof.getAuditTrail().contains("RETENTION_STATUS=PROTECTED"));
        assertTrue(proof.getAuditTrail().contains("OVERRIDE_REASON=CONSENT_WITHDRAWN"));
        assertNotNull(proof.getDigitalSignature());
        assertNotNull(proof.getPqcSignature());
    }
}
