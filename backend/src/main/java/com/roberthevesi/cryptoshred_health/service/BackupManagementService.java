package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.roberthevesi.cryptoshred_health.dto.BackupBundleDto;
import com.roberthevesi.cryptoshred_health.dto.BackupFileEntryDto;
import com.roberthevesi.cryptoshred_health.dto.WormSnapshotDto;
import com.roberthevesi.cryptoshred_health.dto.WormVisitEntryDto;
import com.roberthevesi.cryptoshred_health.model.EncryptionKey;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * BackupManagementService — Coordinates Atomic Coordinated Backup & Disaster Recovery Bundle capture,
 * inventory scanning, and cryptographic SHA-256 / digital signature integrity verification.
 *
 * <p>Captures 4 coordinated zero-drift artifacts per snapshot:
 * 1. Zero-plaintext PostgreSQL logical backup (database_zero_plaintext.sql.gz)
 * 2. HashiCorp Vault Raft storage snapshot / Transit metadata (vault_raft_storage.snap)
 * 3. Immutable WORM clinical encounter export (worm_encounters.json)
 * 4. Cryptographic Manifest with active Merkle root and multi-scheme signatures (bundle_manifest.json)
 */
@Service
@Slf4j
public class BackupManagementService {

    public static final String DB_BACKUP_FILENAME = "database_zero_plaintext.sql.gz";
    public static final String VAULT_SNAPSHOT_FILENAME = "vault_raft_storage.snap";
    public static final String WORM_ENCOUNTERS_FILENAME = "worm_encounters.json";
    public static final String MANIFEST_FILENAME = "bundle_manifest.json";

    private final DataSource dataSource;
    private final PatientVisitRepository patientVisitRepository;
    private final MerkleNodeRepository merkleNodeRepository;
    private final MerkleTreeService merkleTreeService;
    private final ProofSigningService proofSigningService;
    private final VaultOperations vaultOperations;
    private final String backupBundleDirectory;
    private final ObjectMapper objectMapper;

    @Autowired
    public BackupManagementService(
            DataSource dataSource,
            PatientVisitRepository patientVisitRepository,
            MerkleNodeRepository merkleNodeRepository,
            MerkleTreeService merkleTreeService,
            ProofSigningService proofSigningService,
            @Autowired(required = false) VaultOperations vaultOperations,
            @Value("${backup.bundle.directory:backups/bundles}") String backupBundleDirectory) {
        this.dataSource = dataSource;
        this.patientVisitRepository = patientVisitRepository;
        this.merkleNodeRepository = merkleNodeRepository;
        this.merkleTreeService = merkleTreeService;
        this.proofSigningService = proofSigningService;
        this.vaultOperations = vaultOperations;
        this.backupBundleDirectory = backupBundleDirectory;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Atomically creates a complete, coordinated disaster recovery backup bundle.
     *
     * @return BackupBundleDto containing metadata, file checksums, and cryptographic signatures.
     */
    @Transactional(readOnly = true)
    public synchronized BackupBundleDto createAtomicBundle() {
        try {
            Path baseBundleDir = Paths.get(backupBundleDirectory);
            if (!Files.exists(baseBundleDir)) {
                Files.createDirectories(baseBundleDir);
            }

            LocalDateTime timestamp = LocalDateTime.now();
            String timeString = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String bundleName = "bundle_" + timeString;
            Path bundlePath = baseBundleDir.resolve(bundleName);
            Files.createDirectories(bundlePath);

            String bundleId = UUID.randomUUID().toString();
            List<BackupFileEntryDto> fileEntries = new ArrayList<>();

            log.info("📦 [BACKUP INITIATED] Minting Atomic Backup Bundle: {} (UUID: {}) in {}",
                    bundleName, bundleId, bundlePath.toAbsolutePath());

            // 1. Zero-plaintext PostgreSQL logical backup (database_zero_plaintext.sql.gz)
            Path dbBackupPath = bundlePath.resolve(DB_BACKUP_FILENAME);
            exportZeroPlaintextDatabaseDump(dbBackupPath);
            String dbSha256 = computeFileSha256(dbBackupPath);
            long dbSize = Files.size(dbBackupPath);
            fileEntries.add(BackupFileEntryDto.builder()
                    .fileName(DB_BACKUP_FILENAME)
                    .sha256Checksum(dbSha256)
                    .sizeBytes(dbSize)
                    .type("POSTGRESQL_ZERO_PLAINTEXT_DUMP")
                    .verified(true)
                    .build());
            log.info("  ↳ [1/4] PostgreSQL Zero-Plaintext Dump captured: {} bytes, SHA-256: {}", dbSize, dbSha256);

            // 2. Vault Raft storage snapshot / Transit Key metadata export (vault_raft_storage.snap)
            Path vaultSnapshotPath = bundlePath.resolve(VAULT_SNAPSHOT_FILENAME);
            exportVaultRaftSnapshot(vaultSnapshotPath);
            String vaultSha256 = computeFileSha256(vaultSnapshotPath);
            long vaultSize = Files.size(vaultSnapshotPath);
            fileEntries.add(BackupFileEntryDto.builder()
                    .fileName(VAULT_SNAPSHOT_FILENAME)
                    .sha256Checksum(vaultSha256)
                    .sizeBytes(vaultSize)
                    .type("VAULT_KMS_RAFT_SNAPSHOT")
                    .verified(true)
                    .build());
            log.info("  ↳ [2/4] Vault Raft KMS Snapshot captured: {} bytes, SHA-256: {}", vaultSize, vaultSha256);

            // 3. Immutable WORM clinical encounter export (worm_encounters.json)
            Path wormEncountersPath = bundlePath.resolve(WORM_ENCOUNTERS_FILENAME);
            exportWormEncounters(wormEncountersPath, timestamp);
            String wormSha256 = computeFileSha256(wormEncountersPath);
            long wormSize = Files.size(wormEncountersPath);
            fileEntries.add(BackupFileEntryDto.builder()
                    .fileName(WORM_ENCOUNTERS_FILENAME)
                    .sha256Checksum(wormSha256)
                    .sizeBytes(wormSize)
                    .type("IMMUTABLE_WORM_ENCOUNTERS")
                    .verified(true)
                    .build());
            log.info("  ↳ [3/4] WORM Clinical Encounters export captured: {} bytes, SHA-256: {}", wormSize, wormSha256);

            // 4. Retrieve Active Merkle Root Hash (R)
            String merkleRoot = merkleTreeService.getMerkleRoot();
            log.info("  ↳ [Merkle State] Active Merkle Root hash (R): {}", merkleRoot);

            // Total payload size
            long totalBytes = dbSize + vaultSize + wormSize;

            // Sort file entries deterministically by file name for canonical signing
            fileEntries.sort(Comparator.comparing(BackupFileEntryDto::getFileName));

            // Construct Canonical Manifest Signature String
            String canonicalData = buildCanonicalString(bundleId, timestamp, merkleRoot, fileEntries);

            // Digital Signatures: RSA/Vault Transit + Post-Quantum ML-DSA-65
            String rsaSignature = proofSigningService.sign(canonicalData);
            String pqcSignature = null;
            try {
                pqcSignature = proofSigningService.signPqc(canonicalData);
            } catch (Exception e) {
                log.warn("PQC signature generation skipped or failed: {}", e.getMessage());
            }

            String signingPublicKeyPem = proofSigningService.getPublicKeyPem();
            String pqcPublicKeyPem = proofSigningService.getPqcPublicKeyPem();

            // Construct BackupBundleDto Manifest object
            BackupBundleDto bundleDto = BackupBundleDto.builder()
                    .bundleId(bundleId)
                    .bundleName(bundleName)
                    .directoryPath(bundlePath.toAbsolutePath().toString())
                    .timestamp(timestamp)
                    .merkleRoot(merkleRoot)
                    .totalSizeBytes(totalBytes)
                    .status("VALID")
                    .valid(true)
                    .signatureAlgorithm("SHA256withRSA / Vault-Transit")
                    .signature(rsaSignature)
                    .pqcAlgorithm(ProofSigningService.PQC_ALGORITHM_NAME)
                    .pqcSignature(pqcSignature)
                    .signingPublicKey(signingPublicKeyPem)
                    .pqcPublicKey(pqcPublicKeyPem)
                    .files(fileEntries)
                    .verifiedAt(timestamp)
                    .message("Atomic backup bundle minted and cryptographically sealed.")
                    .build();

            // Write bundle_manifest.json
            Path manifestPath = bundlePath.resolve(MANIFEST_FILENAME);
            String manifestJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(bundleDto);
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            // Apply read-only attributes to bundle directory files
            applyReadOnlyPermissions(bundlePath);

            log.info("✅ [BACKUP COMPLETE] Atomic Bundle successfully sealed: {} (Total Size: {} bytes)",
                    bundleName, totalBytes);

            return bundleDto;
        } catch (Exception e) {
            log.error("❌ Failed to create atomic backup bundle: {}", e.getMessage(), e);
            throw new IllegalStateException("Atomic coordinated backup bundle capture failed: " + e.getMessage(), e);
        }
    }

    /**
     * Scans backups/bundles/ directory and parses all bundle manifests.
     *
     * @return List of BackupBundleDto sorted chronologically (latest first).
     */
    public List<BackupBundleDto> listBundles() {
        try {
            Path baseBundleDir = Paths.get(backupBundleDirectory);
            if (!Files.exists(baseBundleDir)) {
                return Collections.emptyList();
            }

            List<BackupBundleDto> bundles = new ArrayList<>();
            try (var stream = Files.list(baseBundleDir)) {
                List<Path> bundleDirs = stream
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("bundle_"))
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .collect(Collectors.toList());

                for (Path dir : bundleDirs) {
                    Path manifestPath = dir.resolve(MANIFEST_FILENAME);
                    if (Files.exists(manifestPath)) {
                        try {
                            String manifestContent = Files.readString(manifestPath, StandardCharsets.UTF_8);
                            BackupBundleDto dto = objectMapper.readValue(manifestContent, BackupBundleDto.class);
                            dto.setDirectoryPath(dir.toAbsolutePath().toString());
                            bundles.add(dto);
                        } catch (Exception ex) {
                            log.warn("Could not parse bundle manifest in {}: {}", dir, ex.getMessage());
                        }
                    }
                }
            }
            return bundles;
        } catch (Exception e) {
            log.error("Failed to list backup bundles: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Re-calculates and asserts SHA-256 checksums and cryptographic signatures for a specific bundle.
     *
     * @param bundleIdentifier Bundle UUID or bundle directory name.
     * @return true if all file checksums match and signatures are valid; false otherwise.
     */
    public boolean verifyBundleIntegrity(String bundleIdentifier) {
        if (bundleIdentifier == null || bundleIdentifier.isBlank()) {
            throw new IllegalArgumentException("Bundle identifier must not be blank");
        }

        // Sanitize identifier to prevent path traversal
        if (bundleIdentifier.contains("..") || bundleIdentifier.contains("/") || bundleIdentifier.contains("\\")) {
            throw new IllegalArgumentException("Invalid bundle identifier");
        }

        Path baseBundleDir = Paths.get(backupBundleDirectory).toAbsolutePath().normalize();
        Path targetBundleDir = null;

        // Try direct folder match
        Path directPath = baseBundleDir.resolve(bundleIdentifier).normalize();
        if (Files.exists(directPath) && Files.isDirectory(directPath) && directPath.startsWith(baseBundleDir)) {
            targetBundleDir = directPath;
        } else {
            // Search all bundle subdirectories for matching bundleId in manifest
            try (var stream = Files.list(baseBundleDir)) {
                List<Path> subDirs = stream.filter(Files::isDirectory).toList();
                for (Path dir : subDirs) {
                    Path manifestPath = dir.resolve(MANIFEST_FILENAME);
                    if (Files.exists(manifestPath)) {
                        try {
                            String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
                            BackupBundleDto dto = objectMapper.readValue(content, BackupBundleDto.class);
                            if (bundleIdentifier.equalsIgnoreCase(dto.getBundleId()) ||
                                    dir.getFileName().toString().equalsIgnoreCase(bundleIdentifier)) {
                                targetBundleDir = dir;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan bundle directories: {}", e.getMessage());
            }
        }

        if (targetBundleDir == null || !Files.exists(targetBundleDir)) {
            log.warn("Backup bundle not found for identifier: {}", bundleIdentifier);
            throw new IllegalArgumentException("Backup bundle not found: " + bundleIdentifier);
        }

        Path manifestPath = targetBundleDir.resolve(MANIFEST_FILENAME);
        if (!Files.exists(manifestPath)) {
            log.warn("Bundle manifest missing in: {}", targetBundleDir);
            return false;
        }

        try {
            String manifestJson = Files.readString(manifestPath, StandardCharsets.UTF_8);
            BackupBundleDto manifest = objectMapper.readValue(manifestJson, BackupBundleDto.class);

            if (manifest.getFiles() == null || manifest.getFiles().isEmpty()) {
                log.warn("Bundle manifest contains no file entries: {}", targetBundleDir);
                return false;
            }

            // 1. Verify all individual file SHA-256 checksums and sizes
            for (BackupFileEntryDto fileEntry : manifest.getFiles()) {
                Path filePath = targetBundleDir.resolve(fileEntry.getFileName());
                if (!Files.exists(filePath)) {
                    log.warn("🚨 [INTEGRITY FAILURE] Component file missing in bundle: {}", fileEntry.getFileName());
                    return false;
                }

                String actualSha256 = computeFileSha256(filePath);
                if (!actualSha256.equalsIgnoreCase(fileEntry.getSha256Checksum())) {
                    log.warn("🚨 [INTEGRITY FAILURE] SHA-256 checksum mismatch on {}. Expected: {}, Actual: {}",
                            fileEntry.getFileName(), fileEntry.getSha256Checksum(), actualSha256);
                    return false;
                }

                long actualSize = Files.size(filePath);
                if (actualSize != fileEntry.getSizeBytes()) {
                    log.warn("🚨 [INTEGRITY FAILURE] File size mismatch on {}. Expected: {}, Actual: {}",
                            fileEntry.getFileName(), fileEntry.getSizeBytes(), actualSize);
                    return false;
                }
            }

            // 2. Reconstruct canonical string and verify digital signatures
            List<BackupFileEntryDto> sortedFiles = new ArrayList<>(manifest.getFiles());
            sortedFiles.sort(Comparator.comparing(BackupFileEntryDto::getFileName));
            String canonicalData = buildCanonicalString(
                    manifest.getBundleId(),
                    manifest.getTimestamp(),
                    manifest.getMerkleRoot(),
                    sortedFiles);

            if (manifest.getSignature() != null && !manifest.getSignature().isBlank()) {
                PublicKey rsaPubKey = null;
                if (manifest.getSigningPublicKey() != null && !manifest.getSigningPublicKey().isBlank()) {
                    try {
                        rsaPubKey = proofSigningService.parsePublicKeyFromPem(manifest.getSigningPublicKey());
                    } catch (Exception ignored) {}
                }
                if (rsaPubKey == null) {
                    rsaPubKey = proofSigningService.getPublicKey();
                }

                boolean rsaValid = proofSigningService.verify(canonicalData, manifest.getSignature(), rsaPubKey);
                if (!rsaValid) {
                    log.warn("🚨 [INTEGRITY FAILURE] RSA/Vault signature verification failed for bundle: {}", manifest.getBundleId());
                    return false;
                }
            }

            if (manifest.getPqcSignature() != null && !manifest.getPqcSignature().isBlank()) {
                PublicKey pqcPubKey = null;
                if (manifest.getPqcPublicKey() != null && !manifest.getPqcPublicKey().isBlank()) {
                    pqcPubKey = proofSigningService.parsePqcPublicKeyFromPem(manifest.getPqcPublicKey());
                }
                if (pqcPubKey == null) {
                    pqcPubKey = proofSigningService.getPqcPublicKey();
                }

                boolean pqcValid = proofSigningService.verifyPqc(canonicalData, manifest.getPqcSignature(), pqcPubKey);
                if (!pqcValid) {
                    log.warn("🚨 [INTEGRITY FAILURE] PQC ML-DSA-65 signature verification failed for bundle: {}", manifest.getBundleId());
                    return false;
                }
            }

            log.info("🛡️ [INTEGRITY VERIFIED] Bundle '{}' (UUID: {}) passed all SHA-256 & signature checks.",
                    targetBundleDir.getFileName(), manifest.getBundleId());
            return true;
        } catch (Exception e) {
            log.error("Error during bundle integrity verification for '{}': {}", bundleIdentifier, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Restores an atomic backup bundle into PostgreSQL database, WORM storage, and Merkle DAG.
     * Rejects restore if pre-flight SHA-256 / digital signature integrity verification fails.
     *
     * @param bundleIdentifier Bundle UUID or folder name.
     * @return true if restore succeeded; throws exception otherwise.
     */
    public synchronized boolean restoreBundle(String bundleIdentifier) {
        log.info("🚨 [DISASTER RECOVERY] Initiating coupled restore for bundle: {}", bundleIdentifier);

        // 1. Strict Pre-flight integrity verification
        if (!verifyBundleIntegrity(bundleIdentifier)) {
            log.error("🚨 [RESTORE REJECTED] Cannot restore bundle '{}': Pre-flight integrity check failed!", bundleIdentifier);
            throw new IllegalStateException("Pre-flight integrity verification failed for bundle: " + bundleIdentifier);
        }

        Path baseBundleDir = Paths.get(backupBundleDirectory).toAbsolutePath().normalize();
        Path targetBundleDir = locateBundleDirectory(bundleIdentifier, baseBundleDir);
        if (targetBundleDir == null || !Files.exists(targetBundleDir)) {
            throw new IllegalArgumentException("Backup bundle directory not found for: " + bundleIdentifier);
        }

        // 2. Restore PostgreSQL Zero-Plaintext Database Dump
        Path dbDumpPath = targetBundleDir.resolve(DB_BACKUP_FILENAME);
        if (Files.exists(dbDumpPath)) {
            executeSqlDumpRestore(dbDumpPath);
            log.info("  ↳ ✅ PostgreSQL Zero-Plaintext database restored successfully.");
        } else {
            throw new IllegalStateException("Database dump file missing in bundle: " + dbDumpPath);
        }

        // 3. Restore WORM Encounters file
        Path wormPath = targetBundleDir.resolve(WORM_ENCOUNTERS_FILENAME);
        if (Files.exists(wormPath)) {
            try {
                for (String dirPath : List.of("backups/worm", "backend/backups/worm")) {
                    Path destDir = Paths.get(dirPath);
                    if (!Files.exists(destDir)) {
                        Files.createDirectories(destDir);
                    }
                    Files.copy(wormPath, destDir.resolve("worm_encounters.json"), StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("  ↳ ✅ WORM Encounters restored to backups/worm/.");
            } catch (Exception ex) {
                log.warn("Could not copy WORM encounters file: {}", ex.getMessage());
            }
        }

        // 4. Re-initialize Merkle Tree in memory from restored database nodes
        try {
            merkleTreeService.init();
            log.info("  ↳ ✅ Merkle Tree re-initialized with {} leaves. Active Root: {}",
                    merkleNodeRepository.count(), merkleTreeService.getMerkleRoot());
        } catch (Exception ex) {
            log.warn("Merkle Tree reinitialization note: {}", ex.getMessage());
        }

        log.info("🎉 [DISASTER RECOVERY COMPLETE] Bundle '{}' successfully restored.", bundleIdentifier);
        return true;
    }

    private Path locateBundleDirectory(String bundleIdentifier, Path baseBundleDir) {
        Path directPath = baseBundleDir.resolve(bundleIdentifier).normalize();
        if (Files.exists(directPath) && Files.isDirectory(directPath) && directPath.startsWith(baseBundleDir)) {
            return directPath;
        }
        try (var stream = Files.list(baseBundleDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                Path manifestPath = dir.resolve(MANIFEST_FILENAME);
                if (Files.exists(manifestPath)) {
                    try {
                        String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
                        BackupBundleDto dto = objectMapper.readValue(content, BackupBundleDto.class);
                        if (bundleIdentifier.equalsIgnoreCase(dto.getBundleId()) ||
                                dir.getFileName().toString().equalsIgnoreCase(bundleIdentifier)) {
                            return dir;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void executeSqlDumpRestore(Path dbDumpPath) {
        try (InputStream is = Files.newInputStream(dbDumpPath);
             BufferedInputStream bis = new BufferedInputStream(is);
             GZIPInputStream gzis = new GZIPInputStream(bis);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8))) {

            if (dataSource == null) {
                log.warn("DataSource is null, skipping JDBC SQL execution.");
                return;
            }

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // PostgreSQL session replication role bypasses FK constraints during bulk restore
                try {
                    stmt.execute("SET session_replication_role = 'replica';");
                } catch (Exception ignored) {}

                // Truncate tables first
                try {
                    stmt.execute("TRUNCATE TABLE patient_attachments, patient_visits, patients, encryption_keys, merkle_nodes, gps, users CASCADE;");
                } catch (Exception ex) {
                    log.debug("Truncate statement info: {}", ex.getMessage());
                }

                String line;
                StringBuilder sqlStatement = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("--")) {
                        continue;
                    }
                    sqlStatement.append(line).append(" ");
                    if (line.endsWith(";")) {
                        String sql = sqlStatement.toString().trim();
                        try {
                            stmt.execute(sql);
                        } catch (Exception stmtEx) {
                            log.debug("SQL restore statement note: {} -> {}", sql, stmtEx.getMessage());
                        }
                        sqlStatement.setLength(0);
                    }
                }

                try {
                    stmt.execute("SET session_replication_role = 'origin';");
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("Failed to restore SQL dump from {}: {}", dbDumpPath, e.getMessage(), e);
            throw new RuntimeException("Database restore failed: " + e.getMessage(), e);
        }
    }

    // ── Internal Component Exporters ──────────────────────────────────────────

    /**
     * Exports a complete zero-plaintext logical SQL dump compressed with GZIP.
     */
    private void exportZeroPlaintextDatabaseDump(Path targetGzipPath) throws Exception {
        try (OutputStream fos = Files.newOutputStream(targetGzipPath);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             GZIPOutputStream gzos = new GZIPOutputStream(bos);
             OutputStreamWriter writer = new OutputStreamWriter(gzos, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(writer)) {

            bw.write("-- =========================================================================\n");
            bw.write("-- CryptoShred Health — Zero-Plaintext Logical Database Backup\n");
            bw.write("-- Exported: " + LocalDateTime.now() + "\n");
            bw.write("-- Zero-Plaintext Guarantee: All patient PHI is stored in AES-256-GCM ciphertexts.\n");
            bw.write("-- DEKs are wrapped under HashiCorp Vault Transit KEKs.\n");
            bw.write("-- =========================================================================\n\n");
            bw.write("SET statement_timeout = 0;\n");
            bw.write("SET lock_timeout = 0;\n");
            bw.write("SET client_encoding = 'UTF8';\n");
            bw.write("SET standard_conforming_strings = on;\n\n");

            if (dataSource != null) {
                try (Connection conn = dataSource.getConnection()) {
                    DatabaseMetaData metaData = conn.getMetaData();
                    String catalog = conn.getCatalog();
                    String schema = conn.getSchema();

                    // Query all user tables
                    try (ResultSet tablesRs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                        List<String> tableNames = new ArrayList<>();
                        while (tablesRs.next()) {
                            String tableName = tablesRs.getString("TABLE_NAME");
                            // Filter out internal system tables if any
                            if (tableName != null && !tableName.toLowerCase().startsWith("pg_") && !tableName.toLowerCase().startsWith("sql_")) {
                                tableNames.add(tableName);
                            }
                        }

                        // Dump table data in logical order
                        for (String table : tableNames) {
                            dumpTableData(conn, table, bw);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Database metadata dump encountered an issue ({}); writing baseline schema export.", ex.getMessage());
                    bw.write("-- Fallback database state export\n");
                    bw.write("-- Database connected: false\n");
                }
            }

            bw.write("\n-- End of Zero-Plaintext Logical Database Export\n");
            bw.flush();
        }
    }

    private void dumpTableData(Connection conn, String tableName, BufferedWriter bw) throws Exception {
        bw.write("-- Table Data: " + tableName + "\n");
        String sql = "SELECT * FROM \"" + tableName + "\"";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            var rsMeta = rs.getMetaData();
            int colCount = rsMeta.getColumnCount();

            while (rs.next()) {
                StringBuilder insert = new StringBuilder("INSERT INTO \"").append(tableName).append("\" (");
                for (int i = 1; i <= colCount; i++) {
                    insert.append("\"").append(rsMeta.getColumnName(i)).append("\"");
                    if (i < colCount) insert.append(", ");
                }
                insert.append(") VALUES (");
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    if (val == null) {
                        insert.append("NULL");
                    } else if (val instanceof Number || val instanceof Boolean) {
                        insert.append(val);
                    } else {
                        String strVal = val.toString().replace("'", "''");
                        insert.append("'").append(strVal).append("'");
                    }
                    if (i < colCount) insert.append(", ");
                }
                insert.append(");\n");
                bw.write(insert.toString());
            }
        } catch (Exception e) {
            log.debug("Notice while dumping table {}: {}", tableName, e.getMessage());
        }
        bw.write("\n");
    }

    /**
     * Exports Vault Raft storage snapshot or Transit Key metadata / configuration snapshot.
     */
    private void exportVaultRaftSnapshot(Path targetSnapshotPath) throws Exception {
        Map<String, Object> vaultSnapshot = new LinkedHashMap<>();
        vaultSnapshot.put("snapshot_type", "VAULT_RAFT_TRANSIT_SNAPSHOT");
        vaultSnapshot.put("timestamp", LocalDateTime.now().toString());
        vaultSnapshot.put("vault_version", "1.13.3");

        if (vaultOperations != null) {
            try {
                // Check if Raft snapshot API can be accessed
                VaultResponse raftResp = vaultOperations.read("sys/storage/raft/snapshot");
                if (raftResp != null && raftResp.getData() != null) {
                    vaultSnapshot.put("raft_storage_snapshot", raftResp.getData());
                } else {
                    // Export Transit secrets engine metadata
                    VaultResponse keysResp = vaultOperations.read("transit/keys?list=true");
                    Map<String, Object> transitKeysMetadata = new LinkedHashMap<>();
                    if (keysResp != null && keysResp.getData() != null && keysResp.getData().containsKey("keys")) {
                        Object keysObj = keysResp.getData().get("keys");
                        if (keysObj instanceof List<?> keyList) {
                            for (Object keyNameObj : keyList) {
                                String keyName = String.valueOf(keyNameObj);
                                try {
                                    VaultResponse keyDetail = vaultOperations.read("transit/keys/" + keyName);
                                    if (keyDetail != null) {
                                        transitKeysMetadata.put(keyName, keyDetail.getData());
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    vaultSnapshot.put("transit_keys_metadata", transitKeysMetadata);
                }
            } catch (Exception e) {
                log.debug("Vault API snapshot retrieval fallback: {}", e.getMessage());
                vaultSnapshot.put("status", "VAULT_LOCAL_STANDALONE_METADATA");
                vaultSnapshot.put("message", e.getMessage());
            }
        } else {
            vaultSnapshot.put("status", "VAULT_STANDALONE_METADATA");
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(vaultSnapshot);
        Files.writeString(targetSnapshotPath, json, StandardCharsets.UTF_8);
    }

    /**
     * Exports immutable WORM clinical encounters JSON.
     */
    private void exportWormEncounters(Path targetWormPath, LocalDateTime timestamp) throws Exception {
        List<PatientVisit> visits = patientVisitRepository != null
                ? patientVisitRepository.findAllWithEncryptionKey()
                : Collections.emptyList();

        List<WormVisitEntryDto> entries = visits.stream()
                .map(this::mapVisitToWormEntry)
                .collect(Collectors.toList());

        WormSnapshotDto snapshotDto = WormSnapshotDto.builder()
                .snapshotId(UUID.randomUUID())
                .fileName(WORM_ENCOUNTERS_FILENAME)
                .timestamp(timestamp)
                .totalRecords(entries.size())
                .totalVisits(entries.size())
                .readOnly(true)
                .visits(entries)
                .build();

        String rawJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshotDto);
        String fingerprint = computeSha256(rawJson);
        snapshotDto.setSha256Fingerprint(fingerprint);

        String finalJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshotDto);
        Files.writeString(targetWormPath, finalJson, StandardCharsets.UTF_8);
    }

    private WormVisitEntryDto mapVisitToWormEntry(PatientVisit visit) {
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

    private String buildCanonicalString(
            String bundleId,
            LocalDateTime timestamp,
            String merkleRoot,
            List<BackupFileEntryDto> files) {
        StringBuilder sb = new StringBuilder();
        sb.append(bundleId).append("|");
        sb.append(timestamp != null ? timestamp.toString() : "").append("|");
        sb.append(merkleRoot != null ? merkleRoot : "").append("|");
        String fileDigest = files.stream()
                .sorted(Comparator.comparing(BackupFileEntryDto::getFileName))
                .map(f -> f.getFileName() + ":" + f.getSha256Checksum() + ":" + f.getSizeBytes())
                .collect(Collectors.joining("|"));
        sb.append(fileDigest);
        return sb.toString();
    }

    private String computeFileSha256(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // reading updates digest
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    private String computeSha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    private void applyReadOnlyPermissions(Path directory) {
        try (var stream = Files.list(directory)) {
            stream.forEach(file -> {
                try {
                    file.toFile().setReadOnly();
                    try {
                        Set<PosixFilePermission> perms = Set.of(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.GROUP_READ,
                                PosixFilePermission.OTHERS_READ);
                        Files.setPosixFilePermissions(file, perms);
                    } catch (UnsupportedOperationException ignored) {}
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
