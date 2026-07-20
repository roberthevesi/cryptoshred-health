package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Signed Proof of Deletion returned after a successful crypto-shred operation.
 * The sha256Hash is a verifiable fingerprint of the audit trail, proving the
 * deletion event occurred at the stated time.
 */
@Data
@AllArgsConstructor
public class DeletionProofResponse {
    private LocalDateTime timestamp;
    private UUID patientRecordId;
    private String requestedBy;
    /** SHA-256 hash of the canonical audit trail string. */
    private String sha256Hash;
    private String status;
    private String auditTrail;
}
