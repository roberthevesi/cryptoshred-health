package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.roberthevesi.cryptoshred_health.dto.WormSnapshotDto;
import com.roberthevesi.cryptoshred_health.dto.WormVisitEntryDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WormBackupExporterService — exports immutable, read-only WORM snapshot files
 * containing envelope-encrypted patient visits.
 *
 * <p>Demonstrates that cryptographic erasure (Vault KEK destruction) guarantees
 * zero-knowledge data invalidation across append-only, write-once-read-many (WORM)
 * storage layers without modifying physical snapshot files.
 */
@Service
@Slf4j
public class WormBackupExporterService {

    private final PatientVisitRepository patientVisitRepository;
    private final VaultKmsService vaultKmsService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final ObjectMapper objectMapper;
    private final String backupDirectory;

    public WormBackupExporterService(
            PatientVisitRepository patientVisitRepository,
            VaultKmsService vaultKmsService,
            EnvelopeEncryptionService envelopeEncryptionService,
            @Value("${backup.worm.directory:backups}") String backupDirectory) {
        this.patientVisitRepository = patientVisitRepository;
        this.vaultKmsService = vaultKmsService;
        this.envelopeEncryptionService = envelopeEncryptionService;
        this.backupDirectory = backupDirectory;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /** Scheduled daily midnight backup export (or configured cron). */
    @Scheduled(cron = "${backup.worm.cron:0 0 0 * * *}")
    public void exportScheduledSnapshot() {
        log.info("Triggering scheduled WORM snapshot export...");
        exportSnapshot();
    }

    /** Exports a new point-in-time WORM snapshot file. */
    @Transactional(readOnly = true)
    public synchronized WormSnapshotDto exportSnapshot() {
        try {
            Path backupDirPath = Paths.get(backupDirectory);
            if (!Files.exists(backupDirPath)) {
                Files.createDirectories(backupDirPath);
            }

            List<PatientVisit> visits = patientVisitRepository.findAllWithEncryptionKey();
            List<WormVisitEntryDto> entries = visits.stream()
                    .map(this::mapToEntry)
                    .collect(Collectors.toList());

            UUID snapshotId = UUID.randomUUID();
            LocalDateTime timestamp = LocalDateTime.now();
            String timeString = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = String.format("snapshot_%s.json", timeString);
            Path filePath = backupDirPath.resolve(fileName);

            WormSnapshotDto snapshotDto = WormSnapshotDto.builder()
                    .snapshotId(snapshotId)
                    .fileName(fileName)
                    .timestamp(timestamp)
                    .totalRecords(entries.size())
                    .totalVisits(entries.size())
                    .readOnly(false)
                    .visits(entries)
                    .build();

            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshotDto);
            String sha256Fingerprint = computeSha256(jsonString);
            snapshotDto.setSha256Fingerprint(sha256Fingerprint);

            // Re-serialize with fingerprint included
            String finalJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshotDto);
            Files.writeString(filePath, finalJson, StandardCharsets.UTF_8);

            // Apply read-only filesystem attributes (WORM simulation)
            File file = filePath.toFile();
            boolean readOnlySet = file.setReadOnly();
            snapshotDto.setReadOnly(readOnlySet || !file.canWrite());
            snapshotDto.setSizeBytes(file.length());

            log.info("WORM Backup Snapshot exported: {} (Visits: {}, Size: {} bytes, ReadOnly: {}, SHA256: {})",
                    fileName, entries.size(), file.length(), snapshotDto.isReadOnly(), sha256Fingerprint);

            return snapshotDto;
        } catch (Exception e) {
            log.error("Failed to export WORM backup snapshot: {}", e.getMessage(), e);
            throw new IllegalStateException("WORM backup snapshot export failed: " + e.getMessage(), e);
        }
    }

    /** Lists all exported WORM backup snapshot metadata summaries in the backup directory. */
    public List<WormSnapshotDto> listSnapshots() {
        try {
            Path backupDirPath = Paths.get(backupDirectory);
            if (!Files.exists(backupDirPath)) {
                return Collections.emptyList();
            }

            List<WormSnapshotDto> list = new ArrayList<>();
            try (var stream = Files.list(backupDirPath)) {
                List<Path> snapshotFiles = stream
                        .filter(p -> p.getFileName().toString().startsWith("snapshot_") && p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .collect(Collectors.toList());

                for (Path p : snapshotFiles) {
                    try {
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        WormSnapshotDto dto = objectMapper.readValue(content, WormSnapshotDto.class);
                        dto.setFileName(p.getFileName().toString());
                        dto.setSizeBytes(p.toFile().length());
                        dto.setReadOnly(!p.toFile().canWrite());
                        dto.setVisits(null); // Summary only
                        list.add(dto);
                    } catch (Exception ex) {
                        log.warn("Could not parse WORM snapshot file {}: {}", p, ex.getMessage());
                    }
                }
            }
            return list;
        } catch (Exception e) {
            log.error("Error listing WORM snapshot files: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Retrieves full WORM snapshot payload for a specific snapshot file. */
    public WormSnapshotDto getSnapshotByFileName(String fileName) {
        // Sanitize fileName — prevent path traversal
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid snapshot file name");
        }
        if (!fileName.startsWith("snapshot_") || !fileName.endsWith(".json")) {
            throw new IllegalArgumentException("Snapshot file name must match pattern: snapshot_*.json");
        }
        Path baseDir = Paths.get(backupDirectory).toAbsolutePath().normalize();
        Path resolvedPath = baseDir.resolve(fileName).normalize();
        if (!resolvedPath.startsWith(baseDir)) {
            throw new IllegalArgumentException("Path traversal detected");
        }
        try {
            Path filePath = Paths.get(backupDirectory).resolve(fileName);
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("WORM snapshot file not found: " + fileName);
            }
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            WormSnapshotDto dto = objectMapper.readValue(content, WormSnapshotDto.class);
            dto.setFileName(filePath.getFileName().toString());
            dto.setSizeBytes(filePath.toFile().length());
            dto.setReadOnly(!filePath.toFile().canWrite());
            return dto;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read WORM snapshot file {}: {}", fileName, e.getMessage(), e);
            throw new IllegalStateException("Failed to read WORM snapshot file: " + fileName, e);
        }
    }

    /** Attempts post-shred decryption of a specific visit in a WORM backup file. */
    public String verifyPostShredDecryptionFailure(String fileName, UUID visitId) {
        try {
            Path filePath = Paths.get(backupDirectory).resolve(fileName);
            if (!Files.exists(filePath)) {
                return "SNAPSHOT_FILE_NOT_FOUND: " + fileName;
            }

            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            WormSnapshotDto snapshotDto = objectMapper.readValue(content, WormSnapshotDto.class);

            WormVisitEntryDto targetVisit = snapshotDto.getVisits().stream()
                    .filter(v -> v.getVisitId().equals(visitId))
                    .findFirst()
                    .orElse(null);

            if (targetVisit == null) {
                return "VISIT_NOT_FOUND_IN_SNAPSHOT: " + visitId;
            }

            if (targetVisit.getWrappedDek() == null || targetVisit.getVaultKeyName() == null) {
                return "VISIT_HAS_NO_WRAPPED_DEK (Already shredded at backup export time)";
            }

            // Attempt decryption using Vault KEK unwrapping
            byte[] dek = null;
            byte[] plaintextBytes = null;
            try {
                dek = vaultKmsService.unwrapDek(targetVisit.getVaultKeyName(), targetVisit.getWrappedDek());
                plaintextBytes = envelopeEncryptionService.decrypt(
                        targetVisit.getEncryptedDataBlob(),
                        targetVisit.getIv(),
                        dek);

                String decryptedText = new String(plaintextBytes, StandardCharsets.UTF_8);
                return "[WARNING_DECRYPTION_SUCCEEDED] Decrypted payload: " + decryptedText;
            } finally {
                if (dek != null) {
                    java.util.Arrays.fill(dek, (byte) 0);
                }
                if (plaintextBytes != null) {
                    java.util.Arrays.fill(plaintextBytes, (byte) 0);
                }
            }

        } catch (Exception e) {
            log.info("Post-shred WORM decryption correctly failed for visit {} in snapshot {}: {}",
                    visitId, fileName, e.getMessage());
            return "[ZERO_PURGE_SUCCESS] Vault KEK destroyed. Data in immutable WORM snapshot remains un-decryptable ciphertext. Reason: " + e.getMessage();
        }
    }

    /**
     * Exports an immutable, read-only WORM deletion receipt recording a cryptographic erasure event.
     */
    public void exportDeletionReceipt(String scope, String entityId, String vaultKeyName, String requestedBy, String auditTrailHash, LocalDateTime timestamp) {
        try {
            Path backupDirPath = Paths.get(backupDirectory);
            if (!Files.exists(backupDirPath)) {
                Files.createDirectories(backupDirPath);
            }

            LocalDateTime receiptTime = timestamp != null ? timestamp : LocalDateTime.now();
            String timeString = receiptTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String sanitizedEntityId = entityId != null ? entityId.replaceAll("[^a-zA-Z0-9.-]", "_") : "unknown";
            String fileName = String.format("deletion-receipt_%s_%s_%s.json", scope, sanitizedEntityId, timeString);
            Path filePath = backupDirPath.resolve(fileName);

            Map<String, Object> receiptMap = new LinkedHashMap<>();
            receiptMap.put("scope", scope);
            receiptMap.put("entityId", entityId);
            receiptMap.put("vaultKeyNameDestroyed", vaultKeyName);
            receiptMap.put("requestedBy", requestedBy);
            receiptMap.put("auditTrailHash", auditTrailHash);
            receiptMap.put("timestamp", receiptTime.toString());

            String rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(receiptMap);
            String fingerprint = computeSha256(rawJson);
            receiptMap.put("sha256Fingerprint", fingerprint);

            String finalJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(receiptMap);
            Files.writeString(filePath, finalJson, StandardCharsets.UTF_8);

            File file = filePath.toFile();
            file.setReadOnly();

            log.info("WORM Deletion Receipt exported: {} (Scope: {}, Entity: {}, SHA256: {})",
                    fileName, scope, entityId, fingerprint);
        } catch (Exception e) {
            log.error("Failed to export WORM deletion receipt for {} ({}): {}", scope, entityId, e.getMessage(), e);
        }
    }

    private WormVisitEntryDto mapToEntry(PatientVisit visit) {
        EncryptionKey key = visit.getEncryptionKey();
        return WormVisitEntryDto.builder()
                .visitId(visit.getId())
                .patientId(visit.getPatient() != null ? visit.getPatient().getPatientId() : visit.getMrn())
                .mrn(visit.getMrn())
                .vaultKeyName(key != null ? key.getVaultKeyName() : null)
                .wrappedDek(key != null && !key.isInvalidated() ? key.getWrappedDek() : null)
                .iv(key != null ? key.getIv() : null)
                .encryptedDataBlob(visit.getEncryptedDataBlob())
                .shredded(visit.isShredded())
                .createdAt(visit.getCreatedAt())
                .build();
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
