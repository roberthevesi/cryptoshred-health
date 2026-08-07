package com.roberthevesi.cryptoshred_health.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * WormSnapshotDto — represents a full point-in-time snapshot
 * written to immutable WORM storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WormSnapshotDto {

    private UUID snapshotId;
    private String fileName;
    private LocalDateTime timestamp;
    private int totalRecords;
    private String sha256Fingerprint;
    private boolean readOnly;
    private long sizeBytes;
    private List<WormRecordEntryDto> records;
}
