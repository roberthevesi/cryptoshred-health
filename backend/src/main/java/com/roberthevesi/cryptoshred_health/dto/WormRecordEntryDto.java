package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WormRecordEntryDto — represents a single patient record entry
 * captured inside an immutable WORM backup snapshot file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WormRecordEntryDto {
    private UUID recordId;
    private String patientName;
    private String mrn;
    private String dateOfBirth;
    private String gender;
    private String vaultKeyName;
    private String wrappedDek;
    private String iv;
    private String encryptedDataBlob;
    private boolean shredded;
    private LocalDateTime createdAt;
}
