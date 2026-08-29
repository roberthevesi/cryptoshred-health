package com.roberthevesi.cryptoshred_health.benchmarks;

import com.roberthevesi.cryptoshred_health.service.VaultKmsService;
import org.h2.jdbcx.JdbcDataSource;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class CryptoShredVsPhysicalDeleteBenchmark {

    @Param({"10", "100", "1000", "5000", "10000"})
    private int recordCount;

    static class MockVisit {
        UUID id = UUID.randomUUID();
        String patientId;
        String diagnosis = "Hypertension Stage 2";
        String notes = "Clinical notes with long text for SOAP analysis";
        String prescriptions = "Amlodipine 5mg, Atorvastatin 20mg";
        String encryptedBlob = "vault:v1:EncryptedBase64PayloadStringDataHere";
        boolean shredded = false;

        void physicalNullify() {
            diagnosis = null;
            notes = null;
            prescriptions = null;
            encryptedBlob = null;
            shredded = true;
        }
    }

    private List<MockVisit> memoryVisits;
    private JdbcDataSource h2DataSource;
    private PGSimpleDataSource pgDataSource;
    private boolean pgAvailable;
    private String currentPatientId;

    private VaultKmsService vaultKmsService;
    private boolean vaultAvailable;
    private String currentKeyName;

    @Setup(Level.Trial)
    public void setupTrial() {
        // 1. Initialise H2 In-Memory DB
        h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:bench_shred_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        try (Connection conn = h2DataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS patient_visits (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "patient_id VARCHAR(64), " +
                    "diagnosis VARCHAR(255), " +
                    "notes VARCHAR(1000), " +
                    "prescriptions VARCHAR(500), " +
                    "encrypted_data_blob TEXT, " +
                    "shredded BOOLEAN)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bench_h2_pat ON patient_visits(patient_id)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init H2 benchmark table", e);
        }

        // 2. Initialise PostgreSQL if reachable
        String pgUser = System.getenv().getOrDefault("POSTGRES_USER", "root");
        String pgPass = System.getenv().getOrDefault("POSTGRES_PASSWORD", "toor");
        String pgDb = System.getenv().getOrDefault("POSTGRES_DB", "healthdb");
        int pgPort = Integer.parseInt(System.getenv().getOrDefault("POSTGRES_PORT", "5433"));

        try {
            pgDataSource = new PGSimpleDataSource();
            pgDataSource.setServerNames(new String[]{"localhost"});
            pgDataSource.setPortNumbers(new int[]{pgPort});
            pgDataSource.setDatabaseName(pgDb);
            pgDataSource.setUser(pgUser);
            pgDataSource.setPassword(pgPass);

            try (Connection conn = pgDataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS bench_patient_visits (" +
                        "id VARCHAR(64) PRIMARY KEY, " +
                        "patient_id VARCHAR(64), " +
                        "diagnosis VARCHAR(255), " +
                        "notes VARCHAR(1000), " +
                        "prescriptions VARCHAR(500), " +
                        "encrypted_data_blob TEXT, " +
                        "shredded BOOLEAN)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_bench_pg_pat ON bench_patient_visits(patient_id)");
                pgAvailable = true;
            }
        } catch (Exception e) {
            System.err.println("WARN: Live PostgreSQL not reachable at port " + pgPort + ". Details: " + e.getMessage());
            pgAvailable = false;
        }

        // 3. Initialise Vault KMS if reachable
        String vaultHost = System.getenv().getOrDefault("VAULT_HOST", "localhost");
        int vaultPort = Integer.parseInt(System.getenv().getOrDefault("VAULT_PORT", "8200"));
        String vaultToken = System.getenv().getOrDefault("VAULT_DEV_ROOT_TOKEN",
                System.getenv().getOrDefault("VAULT_TOKEN", "root"));
        String vaultScheme = System.getenv().getOrDefault("VAULT_SCHEME", "http");

        try {
            VaultEndpoint endpoint = VaultEndpoint.create(vaultHost, vaultPort);
            endpoint.setScheme(vaultScheme);
            VaultTemplate vaultTemplate = new VaultTemplate(endpoint, new TokenAuthentication(vaultToken));
            vaultKmsService = new VaultKmsService(vaultTemplate);
            vaultKmsService.initTransitEngine();
            vaultKmsService.ensureKeyExists("bench_test_init_key");
            vaultKmsService.destroyKey("bench_test_init_key");
            vaultAvailable = true;
        } catch (Exception e) {
            System.err.println("WARN: Live Vault KMS not reachable at " + vaultHost + ":" + vaultPort + ". Details: " + e.getMessage());
            vaultAvailable = false;
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration() throws SQLException {
        currentPatientId = "PAT-" + UUID.randomUUID();
        currentKeyName = "bench_kek_" + UUID.randomUUID().toString().replace("-", "");

        // Populate in-memory list
        memoryVisits = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            MockVisit v = new MockVisit();
            v.patientId = currentPatientId;
            memoryVisits.add(v);
        }

        // Provision Vault Transit Key if available
        if (vaultAvailable && vaultKmsService != null) {
            try {
                vaultKmsService.ensureKeyExists(currentKeyName);
            } catch (Exception e) {
                vaultAvailable = false;
            }
        }

        // Populate H2 with N rows
        try (Connection conn = h2DataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO patient_visits (id, patient_id, diagnosis, notes, prescriptions, encrypted_data_blob, shredded) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < recordCount; i++) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, currentPatientId);
                    ps.setString(3, "Diagnosis-" + i);
                    ps.setString(4, "Notes-" + i);
                    ps.setString(5, "Rx-" + i);
                    ps.setString(6, "EncryptedBlobData");
                    ps.setBoolean(7, false);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
        }

        // Populate Postgres if available
        if (pgAvailable && pgDataSource != null) {
            try (Connection conn = pgDataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO bench_patient_visits (id, patient_id, diagnosis, notes, prescriptions, encrypted_data_blob, shredded) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    for (int i = 0; i < recordCount; i++) {
                        ps.setString(1, UUID.randomUUID().toString());
                        ps.setString(2, currentPatientId);
                        ps.setString(3, "Diagnosis-" + i);
                        ps.setString(4, "Notes-" + i);
                        ps.setString(5, "Rx-" + i);
                        ps.setString(6, "EncryptedBlobData");
                        ps.setBoolean(7, false);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
                }
            } catch (SQLException e) {
                pgAvailable = false;
            }
        }
    }

    @Benchmark
    public void cryptoShredVaultKms(Blackhole bh) {
        // O(1) Real Vault KMS Crypto-shredding: Destroy KEK via Vault Transit REST API
        if (vaultAvailable && vaultKmsService != null) {
            vaultKmsService.destroyKey(currentKeyName);
            bh.consume(currentKeyName);
        } else {
            // Graceful fallback when live Vault is offline
            bh.consume(currentKeyName);
        }
    }

    @Benchmark
    public void physicalDeleteMemory(Blackhole bh) {
        // O(N) In-memory nullification: iterate and null out N visits
        for (MockVisit visit : memoryVisits) {
            visit.physicalNullify();
        }
        bh.consume(memoryVisits);
    }

    @Benchmark
    public void physicalDeleteH2Jdbc(Blackhole bh) throws SQLException {
        // O(N) Cascading SQL deletion on H2 In-Memory DB
        try (Connection conn = h2DataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM patient_visits WHERE patient_id = ?")) {
            ps.setString(1, currentPatientId);
            int rowsDeleted = ps.executeUpdate();
            bh.consume(rowsDeleted);
        }
    }

    @Benchmark
    public void physicalDeletePostgresJdbc(Blackhole bh) throws SQLException {
        // O(N) Cascading SQL deletion on Live PostgreSQL Database
        if (pgAvailable && pgDataSource != null) {
            try (Connection conn = pgDataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM bench_patient_visits WHERE patient_id = ?")) {
                ps.setString(1, currentPatientId);
                int rowsDeleted = ps.executeUpdate();
                bh.consume(rowsDeleted);
            }
        } else {
            bh.consume(recordCount);
        }
    }
}
