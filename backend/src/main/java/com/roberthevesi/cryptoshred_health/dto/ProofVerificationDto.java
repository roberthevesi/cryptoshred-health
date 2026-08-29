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
 * ProofVerificationDto — Canonical representation of a verifiable GDPR Article 17 deletion proof
 * with classical RSA and post-quantum (NIST FIPS 204 ML-DSA-65 / CRYSTALS-Dilithium) hybrid signatures.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProofVerificationDto {

    private String proofVersion;
    private String scope;
    private String entityDescription;
    private UUID visitId;
    private UUID patientRecordId;
    private String patientId;
    private String vaultKeyName;
    private String requestedBy;
    private LocalDateTime timestamp;
    private String status;

    private List<String> coveredStorageLayers;
    private Map<String, String> layerStatus;

    private String auditTrail;
    private String auditTrailHash;

    private String merkleRoot;
    private List<String> merklePath;

    private String signatureAlgorithm;
    private String digitalSignature;

    /** Post-Quantum Cryptography Signature (NIST FIPS 204 ML-DSA-65 / CRYSTALS-Dilithium) */
    private String pqcSignature;
    private String pqcAlgorithm;

    public static ProofVerificationDto from(VerifiableDeletionProofDto proof) {
        if (proof == null) return null;
        return ProofVerificationDto.builder()
                .proofVersion(proof.getProofVersion())
                .scope(proof.getScope())
                .entityDescription(proof.getEntityDescription())
                .visitId(proof.getVisitId())
                .patientRecordId(proof.getPatientRecordId())
                .patientId(proof.getPatientId())
                .vaultKeyName(proof.getVaultKeyName())
                .requestedBy(proof.getRequestedBy())
                .timestamp(proof.getTimestamp())
                .status(proof.getStatus())
                .coveredStorageLayers(proof.getCoveredStorageLayers())
                .layerStatus(proof.getLayerStatus())
                .auditTrail(proof.getAuditTrail())
                .auditTrailHash(proof.getAuditTrailHash())
                .merkleRoot(proof.getMerkleRoot())
                .merklePath(proof.getMerklePath())
                .signatureAlgorithm(proof.getSignatureAlgorithm())
                .digitalSignature(proof.getDigitalSignature())
                .pqcSignature(proof.getPqcSignature())
                .pqcAlgorithm(proof.getPqcAlgorithm())
                .build();
    }

    public VerifiableDeletionProofDto toVerifiableDeletionProofDto() {
        return VerifiableDeletionProofDto.builder()
                .proofVersion(this.proofVersion)
                .scope(this.scope)
                .entityDescription(this.entityDescription)
                .visitId(this.visitId)
                .patientRecordId(this.patientRecordId)
                .patientId(this.patientId)
                .vaultKeyName(this.vaultKeyName)
                .requestedBy(this.requestedBy)
                .timestamp(this.timestamp)
                .status(this.status)
                .coveredStorageLayers(this.coveredStorageLayers)
                .layerStatus(this.layerStatus)
                .auditTrail(this.auditTrail)
                .auditTrailHash(this.auditTrailHash)
                .merkleRoot(this.merkleRoot)
                .merklePath(this.merklePath)
                .signatureAlgorithm(this.signatureAlgorithm)
                .digitalSignature(this.digitalSignature)
                .pqcSignature(this.pqcSignature)
                .pqcAlgorithm(this.pqcAlgorithm)
                .build();
    }
}
