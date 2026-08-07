package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.WormSnapshotDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientRecord;
import com.roberthevesi.cryptoshred_health.repository.PatientRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class WormBackupCryptoShredTest {

    private PatientRecordRepository patientRecordRepository;
    private VaultKmsService vaultKmsService;
    private EnvelopeEncryptionService envelopeEncryptionService;
    private WormBackupExporterService wormBackupExporterService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        patientRecordRepository = Mockito.mock(PatientRecordRepository.class);
        vaultKmsService = Mockito.mock(VaultKmsService.class);
        envelopeEncryptionService = new EnvelopeEncryptionService();

        wormBackupExporterService = new WormBackupExporterService(
                patientRecordRepository,
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

        UUID recordId = UUID.randomUUID();
        String vaultKeyName = "patient-kek-" + recordId;

        PatientRecord record = new PatientRecord();
        record.setId(recordId);
        record.setPatientName("Alice Smith");
        record.setMrn("MRN-12345");
        record.setDateOfBirth("1990-05-15");
        record.setGender("Female");
        record.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
        record.setShredded(false);
        record.setCreatedAt(LocalDateTime.now());

        EncryptionKey key = new EncryptionKey("key-1", vaultKeyName, "wrapped_dek_base64", encryptedPayload.ivBase64());
        record.setEncryptionKey(key);

        when(patientRecordRepository.findAll()).thenReturn(List.of(record));
        when(patientRecordRepository.findAllWithEncryptionKey()).thenReturn(List.of(record));

        // Act
        WormSnapshotDto snapshot = wormBackupExporterService.exportSnapshot();

        // Assert
        assertNotNull(snapshot);
        assertEquals(1, snapshot.getTotalRecords());
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

        UUID recordId = UUID.randomUUID();
        String vaultKeyName = "patient-kek-shredded-" + recordId;
        String wrappedDek = "wrapped_dek_sample";

        PatientRecord record = new PatientRecord();
        record.setId(recordId);
        record.setPatientName("Bob Jones");
        record.setMrn("MRN-99999");
        record.setEncryptedDataBlob(encryptedPayload.ciphertextBase64());
        record.setShredded(false);

        EncryptionKey key = new EncryptionKey("key-999", vaultKeyName, wrappedDek, encryptedPayload.ivBase64());
        record.setEncryptionKey(key);

        when(patientRecordRepository.findAll()).thenReturn(List.of(record));
        when(patientRecordRepository.findAllWithEncryptionKey()).thenReturn(List.of(record));

        // Step 1: Export snapshot prior to shredding
        WormSnapshotDto snapshot = wormBackupExporterService.exportSnapshot();

        // Step 2: Simulate Vault KEK destruction (unwrapDek throws Exception)
        when(vaultKmsService.unwrapDek(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Vault Transit KEK missing or invalid: " + vaultKeyName));

        // Step 3: Attempt post-shred decryption against the immutable WORM snapshot file
        String result = wormBackupExporterService.verifyPostShredDecryptionFailure(snapshot.getFileName(), recordId);

        // Assert
        assertTrue(result.contains("[ZERO_PURGE_SUCCESS]"));
        assertTrue(result.contains("Vault KEK destroyed"));
    }
}
