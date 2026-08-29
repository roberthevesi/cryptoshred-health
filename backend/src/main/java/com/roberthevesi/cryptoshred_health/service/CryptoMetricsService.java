package com.roberthevesi.cryptoshred_health.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * CryptoMetricsService — Exposes custom Micrometer metrics for cryptographic latency,
 * Merkle proof minting, blind indexing lookups, tombstone reconciliations, and backup exports.
 */
@Service
@Slf4j
public class CryptoMetricsService {

    public static final String METRIC_CRYPTO_OPERATIONS = "cryptoshred.crypto.operations";
    public static final String METRIC_MERKLE_PROOF_MINT = "cryptoshred.merkle.proof.mint";
    public static final String METRIC_BLIND_INDEX_LOOKUPS = "cryptoshred.blind.index.lookups";
    public static final String METRIC_TOMBSTONES_PURGED = "cryptoshred.tombstones.purged";
    public static final String METRIC_BACKUP_BUNDLE_DURATION = "cryptoshred.backup.bundle.duration";

    private final MeterRegistry meterRegistry;

    @Autowired
    public CryptoMetricsService(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    @jakarta.annotation.PostConstruct
    public void initMeters() {
        try {
            for (String op : new String[]{"encrypt", "decrypt", "shred", "rotate"}) {
                Timer.builder(METRIC_CRYPTO_OPERATIONS).description("Latency of cryptographic engine operations").tag("operation", op).register(meterRegistry);
            }
            for (String scope : new String[]{"PATIENT_PROFILE", "CLINICAL_VISIT"}) {
                Timer.builder(METRIC_MERKLE_PROOF_MINT).description("Latency of Merkle tree inclusion proof minting").tag("scope", scope).register(meterRegistry);
            }
            for (String field : new String[]{"nhs_number", "mrn", "last_name"}) {
                Counter.builder(METRIC_BLIND_INDEX_LOOKUPS).description("Count of HMAC-SHA256 blind index lookups performed").tag("field", field).register(meterRegistry);
            }
            Counter.builder(METRIC_TOMBSTONES_PURGED).description("Count of resurrected Vault KMS tombstones purged").register(meterRegistry);
            Timer.builder(METRIC_BACKUP_BUNDLE_DURATION).description("Latency of disaster recovery atomic backup bundle capture").register(meterRegistry);
        } catch (Exception e) {
            log.debug("Could not pre-register metric meters: {}", e.getMessage());
        }
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * Records the duration of a cryptographic operation (e.g. encrypt, decrypt, shred, rotate).
     *
     * @param operation operation name (e.g. "encrypt", "decrypt", "shred", "rotate")
     * @param durationNanos elapsed duration in nanoseconds
     */
    public void recordCryptoDuration(String operation, long durationNanos) {
        try {
            Timer.builder(METRIC_CRYPTO_OPERATIONS)
                    .description("Latency of cryptographic engine operations")
                    .tag("operation", operation != null ? operation : "unknown")
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("Could not record crypto duration metric: {}", e.getMessage());
        }
    }

    /**
     * Measures and records the execution time of a cryptographic Runnable action.
     *
     * @param operation operation name
     * @param action executable task
     */
    public void recordCryptoOperation(String operation, Runnable action) {
        if (action == null) return;
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            recordCryptoDuration(operation, System.nanoTime() - start);
        }
    }

    /**
     * Measures and records the execution time of a cryptographic Callable action.
     */
    public <T> T recordCryptoCallable(String operation, Callable<T> action) throws Exception {
        if (action == null) return null;
        long start = System.nanoTime();
        try {
            return action.call();
        } finally {
            recordCryptoDuration(operation, System.nanoTime() - start);
        }
    }

    /**
     * Records the duration of a Merkle inclusion proof minting / generation.
     *
     * @param scope proof scope (e.g. "PATIENT_PROFILE", "CLINICAL_VISIT", "INCLUSION_PROOF")
     * @param durationNanos elapsed duration in nanoseconds
     */
    public void recordMerkleProofMintDuration(String scope, long durationNanos) {
        try {
            Timer.builder(METRIC_MERKLE_PROOF_MINT)
                    .description("Latency of Merkle tree inclusion proof minting")
                    .tag("scope", scope != null ? scope : "UNKNOWN")
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("Could not record Merkle proof mint duration metric: {}", e.getMessage());
        }
    }

    /**
     * Measures and records the duration of a Merkle inclusion proof minting action.
     *
     * @param scope proof scope
     * @param action executable task
     */
    public void recordMerkleProofMint(String scope, Runnable action) {
        if (action == null) return;
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            recordMerkleProofMintDuration(scope, System.nanoTime() - start);
        }
    }

    /**
     * Increments the HMAC-SHA256 blind index lookup counter for a given search field.
     *
     * @param field field name (e.g. "nhs_number", "mrn", "last_name")
     */
    public void recordBlindIndexLookup(String field) {
        try {
            Counter.builder(METRIC_BLIND_INDEX_LOOKUPS)
                    .description("Count of HMAC-SHA256 blind index lookups performed")
                    .tag("field", field != null ? field : "unknown")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.debug("Could not record blind index lookup metric: {}", e.getMessage());
        }
    }

    /**
     * Increments the tombstone purge counter by 1 when a resurrected KMS key is purged.
     */
    public void recordTombstonePurge() {
        recordTombstonesPurged(1);
    }

    /**
     * Increments the tombstone purge counter by count when resurrected KMS keys are purged.
     *
     * @param count number of purged keys
     */
    public void recordTombstonesPurged(int count) {
        if (count <= 0) return;
        try {
            Counter.builder(METRIC_TOMBSTONES_PURGED)
                    .description("Count of resurrected Vault KMS tombstones purged")
                    .register(meterRegistry)
                    .increment(count);
        } catch (Exception e) {
            log.debug("Could not record tombstone purge metric: {}", e.getMessage());
        }
    }

    /**
     * Records the duration of an atomic coordinated disaster recovery backup bundle capture.
     *
     * @param durationNanos elapsed duration in nanoseconds
     */
    public void recordBackupBundleDuration(long durationNanos) {
        try {
            Timer.builder(METRIC_BACKUP_BUNDLE_DURATION)
                    .description("Latency of disaster recovery atomic backup bundle capture")
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("Could not record backup bundle duration metric: {}", e.getMessage());
        }
    }

    /**
     * Measures and records the execution time of a backup bundle capture.
     *
     * @param action executable task
     */
    public void recordBackupBundle(Runnable action) {
        if (action == null) return;
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            recordBackupBundleDuration(System.nanoTime() - start);
        }
    }
}
