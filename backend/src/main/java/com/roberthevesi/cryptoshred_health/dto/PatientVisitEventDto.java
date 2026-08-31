package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PatientVisitEventDto — represents an immutable visit event stream payload
 * stored in the Kafka event log (`patient-record-events`).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientVisitEventDto {
    private UUID eventId;
    private UUID visitId;
    private String patientId; // Synthetic pseudonym identifier (e.g. PAT-12345)
    private String eventType; // VISIT_CREATED, VISIT_UPDATED, VISIT_SHREDDED, PATIENT_CREATED, PATIENT_UPDATED, PATIENT_DEACTIVATED, PATIENT_ACTIVATED, PATIENT_SHREDDED, PATIENT_KEY_ROTATED, KEY_ROTATED
    private String vaultKeyName;
    private String wrappedDek;
    private String iv;
    private String encryptedDataBlob;
    private LocalDateTime timestamp;
}
