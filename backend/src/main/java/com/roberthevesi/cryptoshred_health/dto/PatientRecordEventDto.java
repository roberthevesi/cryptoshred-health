package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PatientRecordEventDto — represents an immutable event stream payload
 * stored in the Kafka event log (`patient-record-events`).
 *
 * <p>Sensitive fields remain encrypted via envelope encryption (DEK/KEK).
 * When the patient's KEK is destroyed in Vault KMS, historical event payloads
 * residing in the Kafka log become permanently un-decryptable ciphertext.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientRecordEventDto {
    private UUID eventId;
    private UUID patientRecordId;
    private String eventType; // RECORD_CREATED, RECORD_UPDATED, RECORD_SHREDDED
    private String vaultKeyName;
    private String wrappedDek;
    private String iv;
    private String encryptedDataBlob;
    private String patientName;
    private LocalDateTime timestamp;
}
