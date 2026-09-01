package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.RetentionPolicyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyServiceTest {

    private RetentionPolicyService retentionPolicyService;

    @BeforeEach
    void setUp() {
        retentionPolicyService = new RetentionPolicyService(8);
    }

    @Test
    void testDefaultRetentionPolicy() {
        RetentionPolicyDto policy = retentionPolicyService.getRetentionPolicy();
        assertNotNull(policy);
        assertEquals(8, policy.getRetentionPeriodYears());
        assertEquals("UK_NHS_COP_2021", policy.getRegulatoryStandard());
        assertNotNull(policy.getDescription());
        assertTrue(policy.getDescription().contains("NHS Records Management Code of Practice"));
        assertNotNull(policy.getLastUpdated());
        assertEquals("SYSTEM_BOOTSTRAP", policy.getUpdatedBy());
    }

    @Test
    void testUpdateToHipaaStandard() {
        RetentionPolicyDto updated = retentionPolicyService.updateRetentionPolicy(6, "admin@cryptoshred.health");
        assertNotNull(updated);
        assertEquals(6, updated.getRetentionPeriodYears());
        assertEquals("US_HIPAA_SEC_164", updated.getRegulatoryStandard());
        assertTrue(updated.getDescription().contains("HIPAA Security Rule"));
        assertEquals("admin@cryptoshred.health", updated.getUpdatedBy());
        assertEquals(6, retentionPolicyService.getRetentionPeriodYears());
    }

    @Test
    void testUpdateToPediatricMaternityStandard() {
        RetentionPolicyDto updated = retentionPolicyService.updateRetentionPolicy(25, "compliance@cryptoshred.health");
        assertNotNull(updated);
        assertEquals(25, updated.getRetentionPeriodYears());
        assertEquals("UK_NHS_PEDIATRIC_MATERNITY_25Y", updated.getRegulatoryStandard());
        assertTrue(updated.getDescription().contains("Pediatric & Maternity"));
    }

    @Test
    void testUpdateToCustomYears() {
        RetentionPolicyDto updated = retentionPolicyService.updateRetentionPolicy(15, "admin@cryptoshred.health");
        assertNotNull(updated);
        assertEquals(15, updated.getRetentionPeriodYears());
        assertEquals("CUSTOM_STATUTORY_POLICY", updated.getRegulatoryStandard());
        assertTrue(updated.getDescription().contains("Custom Administrative Retention Schedule (15 Years)"));
    }

    @Test
    void testInvalidRetentionYearsThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> retentionPolicyService.updateRetentionPolicy(0, "admin"));
        assertThrows(IllegalArgumentException.class, () -> retentionPolicyService.updateRetentionPolicy(-5, "admin"));
        assertThrows(IllegalArgumentException.class, () -> retentionPolicyService.updateRetentionPolicy(101, "admin"));
    }
}
