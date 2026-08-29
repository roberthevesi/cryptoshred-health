package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerkleTombstoneReconciliationTest {

    private PatientRepository patientRepository;
    private PatientVisitRepository patientVisitRepository;
    private MerkleNodeRepository merkleNodeRepository;
    private VaultTransitService vaultTransitService;

    private MerkleTombstoneReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        patientRepository = mock(PatientRepository.class);
        patientVisitRepository = mock(PatientVisitRepository.class);
        merkleNodeRepository = mock(MerkleNodeRepository.class);
        vaultTransitService = mock(VaultTransitService.class);

        reconciliationService = new MerkleTombstoneReconciliationService(
                patientRepository,
                patientVisitRepository,
                merkleNodeRepository,
                vaultTransitService
        );
    }

    @Test
    @DisplayName("Identifies and purges resurrected Vault KEKs for shredded patient profiles and visits")
    void testReconcileTombstonesPurgesResurrectedKeys() {
        UUID patientUuid = UUID.randomUUID();
        String patientKeyName = "patient_" + patientUuid;

        Patient shreddedPatient = new Patient();
        shreddedPatient.setId(patientUuid);
        shreddedPatient.setPatientId("PAT-SHREDDED-1");
        shreddedPatient.setShredded(true);
        EncryptionKey patientKey = new EncryptionKey("key-1", patientKeyName, null, null);
        shreddedPatient.setEncryptionKey(patientKey);

        UUID visitUuid = UUID.randomUUID();
        String visitKeyName = "patient_" + patientUuid + "_visit_" + visitUuid;

        PatientVisit shreddedVisit = new PatientVisit();
        shreddedVisit.setId(visitUuid);
        shreddedVisit.setShredded(true);
        EncryptionKey visitKey = new EncryptionKey("key-2", visitKeyName, null, null);
        shreddedVisit.setEncryptionKey(visitKey);

        when(patientRepository.findByShreddedTrue()).thenReturn(List.of(shreddedPatient));
        when(patientVisitRepository.findByShreddedTrue()).thenReturn(List.of(shreddedVisit));

        // Simulate that patient key was resurrected in Vault KMS, but visit key remains properly destroyed
        when(vaultTransitService.keyExists(patientKeyName)).thenReturn(true);
        when(vaultTransitService.keyExists(visitKeyName)).thenReturn(false);

        int purgedCount = reconciliationService.reconcileTombstones();

        assertEquals(1, purgedCount, "Should purge exactly 1 resurrected key");
        verify(vaultTransitService, times(1)).destroyKey(patientKeyName);
        verify(vaultTransitService, never()).destroyKey(visitKeyName);
    }

    @Test
    @DisplayName("No-op when all tombstone keys are already destroyed in Vault")
    void testReconcileTombstonesWhenNoResurrectedKeys() {
        Patient shreddedPatient = new Patient();
        shreddedPatient.setId(UUID.randomUUID());
        shreddedPatient.setShredded(true);
        shreddedPatient.setEncryptionKey(new EncryptionKey("k1", "key_destroyed_1", null, null));

        when(patientRepository.findByShreddedTrue()).thenReturn(List.of(shreddedPatient));
        when(patientVisitRepository.findByShreddedTrue()).thenReturn(List.of());
        when(vaultTransitService.keyExists(anyString())).thenReturn(false);

        int purged = reconciliationService.reconcileTombstones();

        assertEquals(0, purged, "Should purge 0 keys when all keys are already destroyed");
        verify(vaultTransitService, never()).destroyKey(anyString());
    }
}
