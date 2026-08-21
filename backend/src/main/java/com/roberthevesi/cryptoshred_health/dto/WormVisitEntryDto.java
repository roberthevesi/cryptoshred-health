package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WormVisitEntryDto — represents a single pseudonymized patient visit entry
 * captured inside an immutable WORM backup snapshot file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WormVisitEntryDto {
    private UUID visitId;
    private String patientId;
    private String mrn;
    private String vaultKeyName;
    private String wrappedDek;
    private String iv;
    private String encryptedDataBlob;
    private boolean shredded;
    private LocalDateTime createdAt;
}
