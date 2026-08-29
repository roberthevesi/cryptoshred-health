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

        WormSnapshotDto fullSnapshot = wormBackupExporterService.getSnapshotByFileName(snapshot.getFileName());
        assertNotNull(fullSnapshot.getVisits());
        assertEquals(1, fullSnapshot.getVisits().size());
        assertEquals("wrapped_dek_base64", fullSnapshot.getVisits().get(0).getWrappedDek());
        assertEquals(encryptedPayload.ivBase64(), fullSnapshot.getVisits().get(0).getIv());
        assertEquals(encryptedPayload.ciphertextBase64(), fullSnapshot.getVisits().get(0).getEncryptedDataBlob());
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

        // Verify active snapshot contains wrappedDek
        WormSnapshotDto fullSnapshot = wormBackupExporterService.getSnapshotByFileName(snapshot.getFileName());
        assertNotNull(fullSnapshot.getVisits());
        assertEquals(1, fullSnapshot.getVisits().size());
        assertEquals(wrappedDek, fullSnapshot.getVisits().get(0).getWrappedDek());

        // Step 2: Simulate Vault KEK destruction (unwrapDek throws Exception)
        when(vaultKmsService.unwrapDek(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Vault Transit KEK missing or invalid: " + vaultKeyName));

        // Step 3: Attempt post-shred decryption against the immutable WORM snapshot file
        String result = wormBackupExporterService.verifyPostShredDecryptionFailure(snapshot.getFileName(), visitId);

        // Assert
        assertTrue(result.contains("[ZERO_PURGE_SUCCESS]"), "Expected [ZERO_PURGE_SUCCESS] in result but got: " + result);
    }

    @Test
    void testExportDeletionReceiptCreatesImmutableFileWithFingerprint(@TempDir Path tempDir) throws Exception {
        WormBackupExporterService exporter = new WormBackupExporterService(
                patientVisitRepository,
                vaultKmsService,
                envelopeEncryptionService,
                tempDir.toString()
        );

        LocalDateTime now = LocalDateTime.now();
        exporter.exportDeletionReceipt(
                "PATIENT_PROFILE",
                "PAT-10001",
                "patient_key_10001",
                "dpo_officer",
                "a1b2c3d4e5f67890",
                now
        );

        // Verify receipt file created
        try (var stream = java.nio.file.Files.list(tempDir)) {
            List<Path> files = stream.toList();
            assertEquals(1, files.size());
            Path receiptFile = files.get(0);
            assertTrue(receiptFile.getFileName().toString().startsWith("deletion-receipt_PATIENT_PROFILE_PAT-10001_"));
            assertTrue(receiptFile.getFileName().toString().endsWith(".json"));

            // Verify content
            String content = java.nio.file.Files.readString(receiptFile);
            assertTrue(content.contains("\"scope\" : \"PATIENT_PROFILE\""));
            assertTrue(content.contains("\"entityId\" : \"PAT-10001\""));
            assertTrue(content.contains("\"vaultKeyNameDestroyed\" : \"patient_key_10001\""));
            assertTrue(content.contains("\"requestedBy\" : \"dpo_officer\""));
            assertTrue(content.contains("\"auditTrailHash\" : \"a1b2c3d4e5f67890\""));
            assertTrue(content.contains("\"sha256Fingerprint\""));

            // Verify read-only
            assertFalse(receiptFile.toFile().canWrite(), "Deletion receipt file must be set to read-only (WORM)");
        }
    }
}
