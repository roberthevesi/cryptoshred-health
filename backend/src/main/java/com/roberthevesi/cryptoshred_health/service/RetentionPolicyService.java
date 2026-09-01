package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.RetentionPolicyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RetentionPolicyService — Manages system-wide statutory data retention periods
 * according to UK GDPR Article 17(3)(b) and NHS Records Management Code of Practice 2021.
 */
@Service
@Slf4j
public class RetentionPolicyService {

    public static final int DEFAULT_RETENTION_YEARS = 8;
    public static final String DEFAULT_REGULATORY_STANDARD = "UK_NHS_COP_2021";
    public static final String DEFAULT_DESCRIPTION = "NHS Records Management Code of Practice 2021 (Default 8 Years for General Health Records)";

    private final AtomicInteger retentionPeriodYears;
    private final AtomicReference<String> regulatoryStandard;
    private final AtomicReference<String> description;
    private final AtomicReference<LocalDateTime> lastUpdated;
    private final AtomicReference<String> updatedBy;

    public RetentionPolicyService(@Value("${app.retention.default-period-years:8}") int defaultPeriodYears) {
        int initialYears = defaultPeriodYears > 0 ? defaultPeriodYears : DEFAULT_RETENTION_YEARS;
        this.retentionPeriodYears = new AtomicInteger(initialYears);
        this.regulatoryStandard = new AtomicReference<>(deriveRegulatoryStandard(initialYears));
        this.description = new AtomicReference<>(deriveDescription(initialYears));
        this.lastUpdated = new AtomicReference<>(LocalDateTime.now());
        this.updatedBy = new AtomicReference<>("SYSTEM_BOOTSTRAP");
        log.info("Initialized RetentionPolicyService with default retention: {} years ({})", initialYears, this.regulatoryStandard.get());
    }

    public int getRetentionPeriodYears() {
        return retentionPeriodYears.get();
    }

    public RetentionPolicyDto getRetentionPolicy() {
        return RetentionPolicyDto.builder()
                .retentionPeriodYears(retentionPeriodYears.get())
                .regulatoryStandard(regulatoryStandard.get())
                .description(description.get())
                .lastUpdated(lastUpdated.get())
                .updatedBy(updatedBy.get())
                .build();
    }

    public synchronized RetentionPolicyDto updateRetentionPolicy(int years, String adminUser) {
        if (years < 1 || years > 100) {
            throw new IllegalArgumentException("Retention period must be between 1 and 100 years. Received: " + years);
        }

        retentionPeriodYears.set(years);
        String std = deriveRegulatoryStandard(years);
        String desc = deriveDescription(years);
        regulatoryStandard.set(std);
        description.set(desc);
        LocalDateTime now = LocalDateTime.now();
        lastUpdated.set(now);
        String modifier = (adminUser != null && !adminUser.isBlank()) ? adminUser : "ADMIN";
        updatedBy.set(modifier);

        log.info("Retention policy updated to {} years ({}) by {}", years, std, modifier);

        return RetentionPolicyDto.builder()
                .retentionPeriodYears(years)
                .regulatoryStandard(std)
                .description(desc)
                .lastUpdated(now)
                .updatedBy(modifier)
                .build();
    }

    public synchronized void setRetentionPeriodYears(int years) {
        updateRetentionPolicy(years, "ADMIN");
    }

    private String deriveRegulatoryStandard(int years) {
        return switch (years) {
            case 8 -> "UK_NHS_COP_2021";
            case 6 -> "US_HIPAA_SEC_164";
            case 25 -> "UK_NHS_PEDIATRIC_MATERNITY_25Y";
            default -> "CUSTOM_STATUTORY_POLICY";
        };
    }

    private String deriveDescription(int years) {
        return switch (years) {
            case 8 -> "NHS Records Management Code of Practice 2021 (Default 8 Years for General Health Records)";
            case 6 -> "HIPAA Security Rule 45 CFR § 164.316(b)(2)(i) (6 Years Minimum Retention)";
            case 25 -> "NHS Pediatric & Maternity Extended Retention Rule (25 Years or 26th Birthday)";
            default -> "Custom Administrative Retention Schedule (" + years + " Years)";
        };
    }
}
