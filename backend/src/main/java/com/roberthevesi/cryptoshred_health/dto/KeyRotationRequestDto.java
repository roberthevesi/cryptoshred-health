package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KeyRotationRequestDto — specifies parameters for cryptographic key rotation.
 * Scope can be:
 *  - "ALL": Rotates and rewraps all active keys across patients and visits.
 *  - "PATIENT": Rotates demographic KEK for a specific patientId.
 *  - "VISIT": Rotates clinical KEK for a specific visitId (UUID).
 *  - "KEY": Rotates a specific key by keyId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyRotationRequestDto {
    @Builder.Default
    private String scope = "ALL"; // ALL, PATIENT, VISIT, KEY

    private String targetId; // patientId, visitId (UUID string), or keyId
}
