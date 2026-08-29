package com.roberthevesi.cryptoshred_health.controller;

import com.roberthevesi.cryptoshred_health.dto.BackupBundleDto;
import com.roberthevesi.cryptoshred_health.service.BackupManagementService;
import com.roberthevesi.cryptoshred_health.service.MerkleTombstoneReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AdminBackupController — Dedicated REST API for Disaster Recovery administrators.
 * Provides endpoints for on-demand atomic bundle capture, inventory listing,
 * SHA-256 cryptographic verification, and manual Merkle tombstone reconciliation.
 */
@RestController
@RequestMapping("/api/admin/backups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminBackupController {

    private final BackupManagementService backupManagementService;
    private final MerkleTombstoneReconciliationService merkleTombstoneReconciliationService;

    /**
     * Trigger on-demand atomic coordinated backup bundle capture.
     */
    @PostMapping("/bundle")
    public ResponseEntity<BackupBundleDto> createAtomicBundle() {
        log.info("REST: Admin requested on-demand atomic backup bundle capture.");
        BackupBundleDto bundle = backupManagementService.createAtomicBundle();
        return ResponseEntity.status(HttpStatus.CREATED).body(bundle);
    }

    /**
     * List all available backup bundles and their metadata summaries.
     */
    @GetMapping("/bundles")
    public ResponseEntity<List<BackupBundleDto>> listBundles() {
        List<BackupBundleDto> bundles = backupManagementService.listBundles();
        return ResponseEntity.ok(bundles);
    }

    /**
     * Verify SHA-256 checksums and digital signatures for a specific bundle.
     */
    @PostMapping("/bundles/{bundleId}/verify")
    public ResponseEntity<Map<String, Object>> verifyBundleIntegrity(@PathVariable String bundleId) {
        log.info("REST: Admin requested integrity verification for bundle: {}", bundleId);
        boolean valid = backupManagementService.verifyBundleIntegrity(bundleId);
        return ResponseEntity.ok(Map.of(
                "bundleId", bundleId,
                "valid", valid,
                "status", valid ? "VERIFIED" : "CORRUPTED",
                "verifiedAt", LocalDateTime.now().toString(),
                "message", valid ? "All component checksums and digital signatures verified successfully."
                        : "Integrity check failed: checksum mismatch or signature invalid."
        ));
    }

    /**
     * Restore a specific backup bundle and re-sync database, WORM, and Merkle tree.
     */
    @PostMapping("/bundles/{bundleId}/restore")
    public ResponseEntity<Map<String, Object>> restoreBundle(@PathVariable String bundleId) {
        log.info("REST: Admin requested disaster recovery restore for bundle: {}", bundleId);
        boolean success = backupManagementService.restoreBundle(bundleId);
        int purgedKeys = merkleTombstoneReconciliationService.reconcileTombstones();
        return ResponseEntity.ok(Map.of(
                "bundleId", bundleId,
                "status", success ? "RESTORED" : "FAILED",
                "purgedZombieKeysCount", purgedKeys,
                "restoredAt", LocalDateTime.now().toString(),
                "message", success ? "Disaster recovery bundle restored successfully. Database, WORM, and Merkle states are synchronized."
                        : "Restore failed."
        ));
    }

    /**
     * Trigger immediate Merkle Tombstone reconciliation to purge any resurrected KMS keys.
     */
    @PostMapping("/reconcile-tombstones")
    public ResponseEntity<Map<String, Object>> reconcileTombstones() {
        log.info("REST: Admin triggered immediate Merkle Tombstone reconciliation.");
        int purgedCount = merkleTombstoneReconciliationService.reconcileTombstones();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "purgedKeysCount", purgedCount,
                "timestamp", LocalDateTime.now().toString(),
                "message", "Merkle tombstone reconciliation completed. Resurrected keys purged: " + purgedCount
        ));
    }
}
