package com.roberthevesi.cryptoshred_health.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * ScheduledBackupCronTasks — Automates recurring disaster recovery operations:
 * 1. Daily 02:00 AM Atomic Coordinated Backup Bundle Capture.
 * 2. Periodic 6-Hour Merkle Deletion Tombstone Reconciliation Audit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledBackupCronTasks {

    private final BackupManagementService backupManagementService;
    private final MerkleTombstoneReconciliationService merkleTombstoneReconciliationService;

    /**
     * Daily 02:00 AM atomic coordinated backup bundle capture.
     */
    @Scheduled(cron = "${backup.bundle.cron:0 0 2 * * *}")
    public void executeDailyAtomicBackupBundle() {
        log.info("⏰ [SCHEDULED TASK] Triggering daily atomic coordinated backup bundle capture...");
        try {
            var bundle = backupManagementService.createAtomicBundle();
            log.info("✅ [SCHEDULED TASK] Daily atomic backup bundle minted successfully: {} (UUID: {})",
                    bundle.getBundleName(), bundle.getBundleId());
        } catch (Exception e) {
            log.error("❌ [SCHEDULED TASK] Daily atomic backup bundle capture failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Periodic 6-hour Merkle deletion tombstone reconciler execution.
     * Audits Vault Transit KMS to detect and purge any resurrected KEKs.
     */
    @Scheduled(cron = "${backup.tombstone.cron:0 0 */6 * * *}")
    public void executePeriodicMerkleTombstoneReconciliation() {
        log.info("⏰ [SCHEDULED TASK] Triggering 6-hour Merkle Deletion Tombstone Reconciliation audit...");
        try {
            int purged = merkleTombstoneReconciliationService.reconcileTombstones();
            log.info("✅ [SCHEDULED TASK] Merkle Tombstone Reconciliation completed. Purged {} resurrected keys.", purged);
        } catch (Exception e) {
            log.error("❌ [SCHEDULED TASK] Merkle Tombstone Reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
