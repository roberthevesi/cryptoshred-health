package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * KeyStatusSummaryDto — high-level metrics on KMS encryption keys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyStatusSummaryDto {
    private long totalKeys;
    private long activeKeys;
    private long shreddedKeys;
    private LocalDateTime timestamp;
}
