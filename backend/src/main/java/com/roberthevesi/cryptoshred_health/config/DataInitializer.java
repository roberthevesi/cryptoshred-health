package com.roberthevesi.cryptoshred_health.config;

import com.roberthevesi.cryptoshred_health.service.DataPopulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataPopulationService dataPopulationService;

    @Override
    public void run(String... args) {
        // 0. Drop legacy PostgreSQL enum check constraint if it exists from earlier schema versions
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        } catch (Exception e) {
            log.debug("users_role_check constraint check: {}", e.getMessage());
        }

        // 1. Seed Default Demo Users & Clinicians only
        dataPopulationService.seedDefaultAccounts();
        dataPopulationService.seedCliniciansIfMissing();

        log.info("CryptoShred Health EHR ready. Default demo accounts: doctor@hospital.com, auditor@health.gov, patient@health.org, admin@cryptoshred.health");
        log.info("💡 To populate synthetic patient data, trigger POST /api/admin/seed-data as Admin.");
    }
}
