package com.roberthevesi.cryptoshred_health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VerifiableDeletionProofDto — Standalone, cryptographically signed proof artifact
 * for GDPR Article 17 ("Right to Be Forgotten") compliance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifiableDeletionProofDto {

    private String proofVersion;
    private String scope; // "PATIENT_PROFILE" or "CLINICAL_VISIT"
    private String entityDescription;
    private UUID visitId;
    private UUID patientRecordId; // legacy alias for backward compatibility
    private String patientId;
    private String vaultKeyName;
    private String requestedBy;
    private LocalDateTime timestamp;
    private String status;

    private List<String> coveredStorageLayers;
    private Map<String, String> layerStatus;

    private String auditTrail;
    private String auditTrailHash;
    private String overrideReason;
    private String retentionStatus;

    private String merkleRoot;
    private List<String> merklePath;

    private String signatureAlgorithm;
    private String digitalSignature;

    /** Post-Quantum Cryptography Signature (NIST FIPS 204 ML-DSA-65 / CRYSTALS-Dilithium) */
    private String pqcSignature;
    private String pqcAlgorithm;
}
