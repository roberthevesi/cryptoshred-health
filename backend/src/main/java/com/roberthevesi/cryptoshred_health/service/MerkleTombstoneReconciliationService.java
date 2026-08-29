package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.model.Patient;
import com.roberthevesi.cryptoshred_health.model.PatientVisit;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientRepository;
import com.roberthevesi.cryptoshred_health.repository.PatientVisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MerkleTombstoneReconciliationService — Audits all cryptographic deletion tombstones recorded in the
 * Merkle tree and JPA repositories against Vault Transit KMS to identify and purge any resurrected KEKs
 * (e.g. following legacy KMS backup snapshots or catastrophic database rollbacks).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerkleTombstoneReconciliationService {

    private final PatientRepository patientRepository;
    private final PatientVisitRepository patientVisitRepository;
    private final MerkleNodeRepository merkleNodeRepository;
    private final VaultTransitService vaultTransitService;

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
