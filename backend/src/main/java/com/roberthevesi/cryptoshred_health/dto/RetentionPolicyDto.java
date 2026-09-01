package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RetentionPolicyDto — System retention policy setting and regulatory standard metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicyDto {
    private int retentionPeriodYears;
    private String regulatoryStandard;
    private String description;
    private LocalDateTime lastUpdated;
    private String updatedBy;
}
