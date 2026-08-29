package com.roberthevesi.cryptoshred_health.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.vault.core.VaultOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:actuatortestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.vault.host=localhost",
        "spring.vault.port=8200",
        "spring.vault.token=test-root-token",
        "spring.vault.scheme=http",
        "spring.data.redis.repositories.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "management.endpoints.web.exposure.include=health,info,prometheus",
        "management.endpoint.health.show-details=always",
        "management.health.redis.enabled=false",
        "management.health.vault.enabled=false",
        "management.health.kafka.enabled=false",
        "management.prometheus.metrics.export.enabled=true",
        "management.metrics.tags.application=cryptoshred-health"
})
public class ActuatorPrometheusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CryptoMetricsService cryptoMetricsService;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private org.springframework.vault.core.VaultTemplate vaultTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;



    @Test
    @DisplayName("GET /actuator/health returns HTTP 200 and status UP")
    void testActuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /actuator/info returns HTTP 200")
    void testActuatorInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/prometheus returns HTTP 200 and exports JVM and custom cryptoshred metrics")
    void testActuatorPrometheusMetricsExport() throws Exception {
        // Record test metrics across all custom dimensions
        cryptoMetricsService.recordCryptoDuration("encrypt", TimeUnit.MILLISECONDS.toNanos(15));
        cryptoMetricsService.recordCryptoDuration("decrypt", TimeUnit.MILLISECONDS.toNanos(10));
        cryptoMetricsService.recordCryptoDuration("shred", TimeUnit.MILLISECONDS.toNanos(25));
        cryptoMetricsService.recordMerkleProofMintDuration("PATIENT_PROFILE", TimeUnit.MILLISECONDS.toNanos(5));
        cryptoMetricsService.recordBlindIndexLookup("nhs_number");
        cryptoMetricsService.recordBlindIndexLookup("mrn");
        cryptoMetricsService.recordTombstonePurge();
        cryptoMetricsService.recordBackupBundleDuration(TimeUnit.MILLISECONDS.toNanos(50));

        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // 1. Verify standard JVM metrics exported
        assertTrue(responseBody.contains("jvm_memory_used_bytes") || responseBody.contains("jvm_"),
                "Prometheus output should contain standard JVM metrics");

        // 2. Verify custom cryptoshred meters exported
        assertTrue(responseBody.contains("cryptoshred_crypto_operations_seconds"),
                "Prometheus output should contain cryptoshred_crypto_operations_seconds metric");
        assertTrue(responseBody.contains("cryptoshred_merkle_proof_mint_seconds"),
                "Prometheus output should contain cryptoshred_merkle_proof_mint_seconds metric");
        assertTrue(responseBody.contains("cryptoshred_blind_index_lookups_total"),
                "Prometheus output should contain cryptoshred_blind_index_lookups_total metric");
        assertTrue(responseBody.contains("cryptoshred_tombstones_purged_total"),
                "Prometheus output should contain cryptoshred_tombstones_purged_total metric");
        assertTrue(responseBody.contains("cryptoshred_backup_bundle_duration_seconds"),
                "Prometheus output should contain cryptoshred_backup_bundle_duration_seconds metric");
    }
}
