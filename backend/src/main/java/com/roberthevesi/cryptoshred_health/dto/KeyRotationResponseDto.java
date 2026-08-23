package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * KeyRotationResponseDto — detailed outcome of a cryptographic key rotation operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyRotationResponseDto {
    private String status; // SUCCESS, PARTIAL, FAILED
    private String scope;
    private int totalProcessed;
    private int rotatedCount;
    private int skippedCount;
    private long durationMs;
    private LocalDateTime timestamp;
    private List<KeyRotationDetailDto> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyRotationDetailDto {
        private String keyId;
        private String vaultKeyName;
        private String status; // ROTATED, SKIPPED_INVALIDATED, FAILED
        private int previousVersion;
        private int newVersion;
        private String message;
    }
}
