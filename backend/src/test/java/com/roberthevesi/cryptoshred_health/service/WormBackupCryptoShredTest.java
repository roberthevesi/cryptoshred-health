package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.WormSnapshotDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class WormBackupCryptoShredTest {

    private PatientVisitRepository patientVisitRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private WormBackupExporterService wormBackupExporterService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        patientVisitRepository = Mockito.mock(PatientVisitRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();

        wormBackupExporterService = new WormBackupExporterService(
                patientVisitRepository,
                vaultKmsService,
                envelopeEncryptionService,
                tempDir.toString()
        );
    }

    @Test
    void testWormSnapshotExportCreatesReadOnlyFileWithFingerprint() {
        // Arrange
        byte[] dek = envelopeEncryptionService.generateDek();
        String sensitiveNotes = "Patient diagnosed with hypertension.";
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(sensitiveNotes.getBytes(StandardCharsets.UTF_8), dek);

        UUID visitId = UUID.randomUUID();
        String vaultKeyName = "patient_d3b07384-d113-4673-9080-87a41ec62762_visit_" + visitId;

        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatientName("Alice Smith");
        visit.setMrn("MRN-12345");
        visit.setDateOfBirth("1990-05-15");
        visit.setGender("Female");
        visit.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
        visit.setShredded(false);
        visit.setCreatedAt(LocalDateTime.now());

        EncryptionKey key = new EncryptionKey("key-1", vaultKeyName, "wrapped_dek_base64", encryptedPayload.ivBase64());
        visit.setEncryptionKey(key);

        when(patientVisitRepository.findAll()).thenReturn(List.of(visit));
        when(patientVisitRepository.findAllWithEncryptionKey()).thenReturn(List.of(visit));

        // Act
        WormSnapshotDto snapshot = wormBackupExporterService.exportSnapshot();

        // Assert
        assertNotNull(snapshot);
        assertEquals(1, snapshot.getTotalVisits());
        assertNotNull(snapshot.getSha256Fingerprint());
        assertTrue(snapshot.getSha256Fingerprint().length() > 0);

        List<WormSnapshotDto> snapshots = wormBackupExporterService.listSnapshots();
        assertEquals(1, snapshots.size());
        assertEquals(snapshot.getFileName(), snapshots.get(0).getFileName());
    }

    @Test
    void testPostShredWormSnapshotDecryptionFailsWhenVaultKekDestroyed() {
        // Arrange
        byte[] dek = envelopeEncryptionService.generateDek();
        String sensitiveNotes = "Top secret oncology consultation data.";
        EnvelopeEncryptionService.EncryptedPayload encryptedPayload =
                envelopeEncryptionService.encrypt(sensitiveNotes.getBytes(StandardCharsets.UTF_8), dek);

        UUID visitId = UUID.randomUUID();
        String vaultKeyName = "patient_shredded-patient-uuid_visit_" + visitId;
        String wrappedDek = "wrapped_dek_sample";



        PatientVisit visit = new PatientVisit();
        visit.setId(visitId);
        visit.setPatientName("Bob Jones");
        visit.setMrn("MRN-99999");
        visit.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
        visit.setShredded(false);

        EncryptionKey key = new EncryptionKey("key-999", vaultKeyName, wrappedDek, encryptedPayload.ivBase64());
        visit.setEncryptionKey(key);

        when(patientVisitRepository.findAll()).thenReturn(List.of(visit));
        when(patientVisitRepository.findAllWithEncryptionKey()).thenReturn(List.of(visit));

        // Step 1: Export snapshot prior to shredding
        WormSnapshotDto snapshot = wormBackupExporterService.exportSnapshot();

        // Step 2: Simulate Vault KEK destruction (unwrapDek throws Exception)
        when(vaultKmsService.unwrapDek(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Vault Transit KEK missing or invalid: " + vaultKeyName));

        // Step 3: Attempt post-shred decryption against the immutable WORM snapshot file
        String result = wormBackupExporterService.verifyPostShredDecryptionFailure(snapshot.getFileName(), visitId);

        // Assert
        assertTrue(result.contains("[ZERO_PURGE_SUCCESS]"));
        assertTrue(result.contains("Vault KEK destroyed"));
    }
}
