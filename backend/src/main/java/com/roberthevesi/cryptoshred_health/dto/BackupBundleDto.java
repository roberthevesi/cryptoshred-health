package com.roberthevesi.cryptoshred_health.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BackupBundleDto — represents the complete atomic backup bundle metadata,
 * active Merkle Root hash (R), file entry checksums, RSA & PQC signatures, and verification status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupBundleDto {
    private String bundleId;
    private String bundleName;
    private String directoryPath;
    private LocalDateTime timestamp;
    private String merkleRoot;
    private long totalSizeBytes;
    private String status;
    private boolean valid;
    private String signatureAlgorithm;
    private String signature;
    private String pqcAlgorithm;
    private String pqcSignature;
    private String signingPublicKey;
    private String pqcPublicKey;
    private List<BackupFileEntryDto> files;
    private LocalDateTime verifiedAt;
    private String message;
}
