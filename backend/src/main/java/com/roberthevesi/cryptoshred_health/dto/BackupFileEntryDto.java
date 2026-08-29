package com.roberthevesi.cryptoshred_health.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BackupFileEntryDto — represents metadata and cryptographic checksum
 * of an individual component file within a backup bundle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupFileEntryDto {
    private String fileName;
    private String sha256Checksum;
    private long sizeBytes;
    private String type;
    private Boolean verified;
}
