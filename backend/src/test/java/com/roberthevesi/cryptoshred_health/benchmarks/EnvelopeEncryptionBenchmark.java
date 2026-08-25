package com.roberthevesi.cryptoshred_health.benchmarks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roberthevesi.cryptoshred_health.service.EnvelopeEncryptionService;
import com.roberthevesi.cryptoshred_health.service.VaultKmsService;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class EnvelopeEncryptionBenchmark {

    private static final String BENCH_KEY = "bench_envelope_kek";

    private EnvelopeEncryptionService envelopeEncryptionService;
    private VaultKmsService vaultKmsService;
    private ObjectMapper objectMapper;

    private byte[] samplePayloadBytes;
    private String sampleJson;
    private byte[] cachedDek;
    private String cachedWrappedDek;
    private EnvelopeEncryptionService.EncryptedPayload cachedEncrypted;
    private boolean vaultAvailable;

    @Setup(Level.Trial)
    public void setUp() {
        objectMapper = new ObjectMapper();
        envelopeEncryptionService = new EnvelopeEncryptionService();

        Map<String, Object> clinicalRecord = Map.of(
                "patientId", "PAT-BENCH-001",
                "diagnosis", "Primary Essential Hypertension & Hyperlipidemia",
                "chiefComplaint", "Patient reports recurring mild headaches and fatigue",
                "soapSubjective", "Patient reports moderate daily stress, compliant with sodium restriction.",
                "soapObjective", "BP: 142/88 mmHg, HR: 74 bpm, BMI: 27.2",
                "soapAssessment", "Hypertension stage 1, well-managed",
                "soapPlan", "Prescribe Amlodipine 5mg OD, follow up in 90 days",
                "prescriptions", "Amlodipine 5mg PO Daily, Atorvastatin 20mg PO QPM"
        );

        try {
            sampleJson = objectMapper.writeValueAsString(clinicalRecord);
            samplePayloadBytes = sampleJson.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        cachedDek = envelopeEncryptionService.generateDek();
        cachedEncrypted = envelopeEncryptionService.encrypt(samplePayloadBytes, cachedDek);

        // Connect to live Vault if available
        String vaultToken = System.getenv().getOrDefault("VAULT_DEV_ROOT_TOKEN", "root");
        String vaultHost = System.getenv().getOrDefault("VAULT_HOST", "localhost");
        int vaultPort = Integer.parseInt(System.getenv().getOrDefault("VAULT_PORT", "8200"));

        try {
            VaultEndpoint endpoint = VaultEndpoint.create(vaultHost, vaultPort);
            endpoint.setScheme("http");
            VaultTemplate vaultTemplate = new VaultTemplate(endpoint, new TokenAuthentication(vaultToken));
            vaultKmsService = new VaultKmsService(vaultTemplate);
            vaultKmsService.ensureKeyExists(BENCH_KEY);
            cachedWrappedDek = vaultKmsService.wrapDek(BENCH_KEY, cachedDek);
            vaultAvailable = true;
        } catch (Exception e) {
            System.err.println("WARN: Live Vault not available at " + vaultHost + ":" + vaultPort + ". Fallback to simulated latency. Details: " + e.getMessage());
            vaultAvailable = false;
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (vaultAvailable && vaultKmsService != null) {
            try {
                vaultKmsService.destroyKey(BENCH_KEY);
            } catch (Exception ignored) {}
        }
    }

    @Benchmark
    public void plaintextBaseline(Blackhole bh) throws Exception {
        byte[] bytes = sampleJson.getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        bh.consume(bytes);
        bh.consume(hash);
    }

    @Benchmark
    public void localAesGcmEncryption(Blackhole bh) {
        byte[] dek = envelopeEncryptionService.generateDek();
        EnvelopeEncryptionService.EncryptedPayload payload = envelopeEncryptionService.encrypt(samplePayloadBytes, dek);
        byte[] decrypted = envelopeEncryptionService.decrypt(payload.ciphertextBase64(), payload.ivBase64(), dek);
        bh.consume(payload);
        bh.consume(decrypted);
    }

    @Benchmark
    public void vaultEnvelopeEncryption(Blackhole bh) {
        if (vaultAvailable) {
            byte[] dek = envelopeEncryptionService.generateDek();
            String wrapped = vaultKmsService.wrapDek(BENCH_KEY, dek);
            EnvelopeEncryptionService.EncryptedPayload payload = envelopeEncryptionService.encrypt(samplePayloadBytes, dek);

            byte[] unwrappedDek = vaultKmsService.unwrapDek(BENCH_KEY, wrapped);
            byte[] decrypted = envelopeEncryptionService.decrypt(payload.ciphertextBase64(), payload.ivBase64(), unwrappedDek);

            bh.consume(wrapped);
            bh.consume(payload);
            bh.consume(unwrappedDek);
            bh.consume(decrypted);
        } else {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {}
            bh.consume(cachedEncrypted);
        }
    }
}
