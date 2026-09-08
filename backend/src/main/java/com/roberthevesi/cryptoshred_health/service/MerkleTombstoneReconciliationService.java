package com.roberthevesi.cryptoshred_health.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MerkleTombstoneReconciliationService — Audits all cryptographic deletion tombstones recorded in the
 * Merkle tree, JPA repositories, and WORM backup receipts against Vault Transit KMS to identify and purge any resurrected KEKs
 * (e.g. following legacy KMS backup snapshots or catastrophic database rollbacks).
 */
@Service
@Slf4j
public class MerkleTombstoneReconciliationService {

    private final PatientRepository patientRepository;
    private final PatientVisitRepository patientVisitRepository;
    private final MerkleNodeRepository merkleNodeRepository;
    private final VaultTransitService vaultTransitService;

    @Autowired(required = false)
    private CryptoMetricsService cryptoMetricsService;

    @Value("${backup.worm.directory:backups}")
    private String backupDirectory = "backups";

    private final ObjectMapper objectMapper;

    public MerkleTombstoneReconciliationService(
            PatientRepository patientRepository,
            PatientVisitRepository patientVisitRepository,
            MerkleNodeRepository merkleNodeRepository,
            VaultTransitService vaultTransitService
    ) {
        this(patientRepository, patientVisitRepository, merkleNodeRepository, vaultTransitService, null, "backups", new ObjectMapper());
    }

    public MerkleTombstoneReconciliationService(
            PatientRepository patientRepository,
            PatientVisitRepository patientVisitRepository,
            MerkleNodeRepository merkleNodeRepository,
            VaultTransitService vaultTransitService,
            String backupDirectory
    ) {
        this(patientRepository, patientVisitRepository, merkleNodeRepository, vaultTransitService, null, backupDirectory, new ObjectMapper());
    }

    public MerkleTombstoneReconciliationService(
            PatientRepository patientRepository,
            PatientVisitRepository patientVisitRepository,
            MerkleNodeRepository merkleNodeRepository,
            VaultTransitService vaultTransitService,
            CryptoMetricsService cryptoMetricsService
    ) {
        this(patientRepository, patientVisitRepository, merkleNodeRepository, vaultTransitService, cryptoMetricsService, "backups", new ObjectMapper());
    }

    public MerkleTombstoneReconciliationService(
            PatientRepository patientRepository,
            PatientVisitRepository patientVisitRepository,
            MerkleNodeRepository merkleNodeRepository,
            VaultTransitService vaultTransitService,
            CryptoMetricsService cryptoMetricsService,
            String backupDirectory
    ) {
        this(patientRepository, patientVisitRepository, merkleNodeRepository, vaultTransitService, cryptoMetricsService, backupDirectory, new ObjectMapper());
    }

    @Autowired
    public MerkleTombstoneReconciliationService(
            PatientRepository patientRepository,
            PatientVisitRepository patientVisitRepository,
            MerkleNodeRepository merkleNodeRepository,
            VaultTransitService vaultTransitService,
            @Autowired(required = false) CryptoMetricsService cryptoMetricsService,
            @Value("${backup.worm.directory:backups}") String backupDirectory,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.patientRepository = patientRepository;
        this.patientVisitRepository = patientVisitRepository;
        this.merkleNodeRepository = merkleNodeRepository;
        this.vaultTransitService = vaultTransitService;
        this.cryptoMetricsService = cryptoMetricsService;
        this.backupDirectory = (backupDirectory != null && !backupDirectory.isBlank()) ? backupDirectory : "backups";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public void setBackupDirectory(String backupDirectory) {
        this.backupDirectory = backupDirectory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🛡️ Initiating Merkle Deletion Tombstone Reconciliation audit on ApplicationReadyEvent...");
        int purged = reconcileTombstones();
        log.info("🛡️ Merkle Deletion Tombstone Reconciliation audit completed. Purged {} resurrected KMS keys.", purged);
    }

    /**
     * Scans all shredded patient records and clinical visits, querying Vault Transit to verify
     * that their associated KEKs remain strictly destroyed. If a key exists (resurrected state),
     * it is immediately purged to maintain absolute cryptographic forward secrecy and GDPR Article 17 compliance.
     *
     * @return count of resurrected keys successfully purged
     */
    public int reconcileTombstones() {
        int purgedCount = 0;
        Set<String> tombstoneKeyNames = new HashSet<>();

        // 1. Scan shredded patient profiles
        List<Patient> shreddedPatients = patientRepository.findByShreddedTrue();
        for (Patient patient : shreddedPatients) {
            if (patient.getEncryptionKey() != null && patient.getEncryptionKey().getVaultKeyName() != null) {
                tombstoneKeyNames.add(patient.getEncryptionKey().getVaultKeyName());
            }
            if (patient.getId() != null) {
                tombstoneKeyNames.add("patient_" + patient.getId());
            }
        }

        // 2. Scan shredded clinical visit records
        List<PatientVisit> shreddedVisits = patientVisitRepository.findByShreddedTrue();
        for (PatientVisit visit : shreddedVisits) {
            if (visit.getEncryptionKey() != null && visit.getEncryptionKey().getVaultKeyName() != null) {
                tombstoneKeyNames.add(visit.getEncryptionKey().getVaultKeyName());
            }
        }

        // 3. Scan WORM deletion receipts directory
        int initialTombstones = tombstoneKeyNames.size();
        Path backupDirPath = Paths.get(backupDirectory != null ? backupDirectory : "backups");
        if (Files.exists(backupDirPath) && Files.isDirectory(backupDirPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDirPath, "deletion-receipt_*.json")) {
                for (Path receiptPath : stream) {
                    try {
                        JsonNode node = objectMapper.readTree(receiptPath.toFile());
                        if (node.hasNonNull("vaultKeyNameDestroyed")) {
                            String keyName = node.get("vaultKeyNameDestroyed").asText();
                            if (!keyName.isBlank()) {
                                tombstoneKeyNames.add(keyName);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse WORM deletion receipt {}: {}", receiptPath.getFileName(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan WORM deletion receipts directory {}: {}", backupDirPath, e.getMessage());
            }
        }
        int wormTombstonesCount = tombstoneKeyNames.size() - initialTombstones;
        log.info("Discovered {} tombstone keys from WORM deletion receipts in directory: {}", wormTombstonesCount, backupDirPath);

        log.info("Auditing {} tombstone KEK references against Vault Transit...", tombstoneKeyNames.size());

        // 3. Inspect tombstone keys against Vault KMS in memory
        List<String> existingVaultKeys = vaultTransitService.listKeys();
        Set<String> existingVaultKeySet = new HashSet<>(existingVaultKeys);

        for (String keyName : tombstoneKeyNames) {
            if (keyName == null || keyName.isBlank()) {
                continue;
            }
            try {
                boolean exists = !existingVaultKeySet.isEmpty()
                        ? existingVaultKeySet.contains(keyName)
                        : vaultTransitService.keyExists(keyName);

                if (exists) {
                    log.warn("🚨 [TOMBSTONE ALERT] Resurrected Vault KEK detected for shredded entity: '{}'. Purging key immediately...", keyName);
                    vaultTransitService.destroyKey(keyName);
                    purgedCount++;
                    if (cryptoMetricsService != null) {
                        cryptoMetricsService.recordTombstonePurge();
                    }
                    log.info("✅ Resurrected Vault KEK '{}' successfully destroyed.", keyName);
                }
            } catch (Exception e) {
                log.error("Failed to reconcile tombstone key '{}': {}", keyName, e.getMessage());
            }
        }

        log.info("🛡️ [RECONCILIATION COMPLETE] Audited {} tombstones against Vault Transit. Resurrected keys purged: {}",
                tombstoneKeyNames.size(), purgedCount);
        return purgedCount;
    }
}
