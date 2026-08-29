package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.ProofVerificationDto;
import com.roberthevesi.cryptoshred_health.dto.ProofVerificationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.LocalDateTime;

/**
 * ProofVerificationService — Implements dual hybrid cryptographic verification for GDPR Article 17
 * verifiable deletion proofs, combining Classical RSA-2048 (Vault Transit / local) and
 * Post-Quantum NIST FIPS 204 ML-DSA-65 (CRYSTALS-Dilithium) digital signatures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProofVerificationService {

    public static final String PQC_ALGORITHM = "ML-DSA-65 (NIST FIPS 204)";
    public static final String CLASSICAL_ALGORITHM = "SHA256withRSA";
    public static final String HYBRID_ALGORITHM = "Hybrid RSA-2048 + ML-DSA-65 (NIST FIPS 204)";

    private final ProofSigningService proofSigningService;
    private final MerkleTreeService merkleTreeService;

    /**
     * Classical signing via Vault Transit RSA-2048 or local fallback keypair.
     */
    public String signClassical(String canonicalPayload) {
        return proofSigningService.sign(canonicalPayload);
    }

    /**
     * Post-Quantum signing via NIST FIPS 204 ML-DSA-65 (CRYSTALS-Dilithium3).
     */
    public String signPostQuantum(String canonicalPayload) {
        return proofSigningService.signPqc(canonicalPayload);
    }

    /**
     * Verifies Classical RSA signature.
     */
    public boolean verifyClassical(String canonicalPayload, String signature) {
        return proofSigningService.verify(canonicalPayload, signature);
    }

    /**
     * Verifies Post-Quantum ML-DSA signature.
     */
    public boolean verifyPostQuantum(String canonicalPayload, String pqcSignature) {
        return proofSigningService.verifyPqc(canonicalPayload, pqcSignature);
    }

    /**
     * Verifies Post-Quantum ML-DSA signature using a supplied public key.
     */
    public boolean verifyPostQuantum(String canonicalPayload, String pqcSignature, PublicKey publicKey) {
        return proofSigningService.verifyPqc(canonicalPayload, pqcSignature, publicKey);
    }

    /**
     * Verifies a ProofVerificationDto artifact against payload integrity, classical RSA signature,
     * post-quantum ML-DSA-65 signature, and Merkle tree inclusion path.
     */
    public ProofVerificationResponseDto verifyProofArtifact(ProofVerificationDto proof) {
        if (proof == null) {
            return buildNullProofResponse();
        }
        return verifyProofArtifact(proof.toVerifiableDeletionProofDto());
    }

    /**
     * Verifies a VerifiableDeletionProofDto artifact against payload integrity, classical RSA signature,
     * post-quantum ML-DSA-65 signature, and Merkle tree inclusion path.
     */
    public ProofVerificationResponseDto verifyProofArtifact(VerifiableDeletionProofDto proof) {
        if (proof == null) {
            return buildNullProofResponse();
        }

        // 1. Check SHA-256 payload integrity
        String expectedHash = sha256Hex(proof.getAuditTrail());
        boolean payloadIntegrityValid = proof.getAuditTrailHash() != null &&
                expectedHash.equalsIgnoreCase(proof.getAuditTrailHash());

        // 2. Resolve entity identifier for canonical signature payload
        String identifier = resolveEntityIdentifier(proof);
        String canonicalPayload = buildCanonicalSignPayload(
                identifier, proof.getTimestamp(), proof.getAuditTrailHash(), proof.getMerkleRoot());

        // 3. Check Classical RSA Digital Signature
        boolean signatureValid = proofSigningService.verify(canonicalPayload, proof.getDigitalSignature());

        // 4. Check Post-Quantum ML-DSA-65 Signature
        boolean pqcSignatureValid = false;
        if (proof.getPqcSignature() != null && !proof.getPqcSignature().isBlank()) {
            pqcSignatureValid = proofSigningService.verifyPqc(canonicalPayload, proof.getPqcSignature());
        }

        // 5. Check Merkle tree inclusion if root and path provided
        boolean merkleValid = true;
        if (proof.getMerkleRoot() != null && proof.getMerklePath() != null) {
            merkleValid = merkleTreeService.verifyInclusion(
                    proof.getAuditTrailHash(), proof.getMerklePath(), proof.getMerkleRoot());
        }

        // Overall validity: payload, RSA, and Merkle must pass. If PQC signature is provided, it must also pass.
        boolean overallValid = payloadIntegrityValid && signatureValid && merkleValid
                && (proof.getPqcSignature() == null || pqcSignatureValid);

        String message;
        if (!payloadIntegrityValid) {
            message = "FAILED: Audit trail payload has been tampered with (SHA-256 hash mismatch).";
        } else if (!signatureValid) {
            message = "FAILED: Classical RSA digital signature is invalid or forged.";
        } else if (proof.getPqcSignature() != null && !pqcSignatureValid) {
            message = "FAILED: Post-Quantum ML-DSA-65 signature is invalid or forged.";
        } else if (!merkleValid) {
            message = "FAILED: Merkle tree inclusion path verification failed.";
        } else {
            message = "SUCCESS: Deletion proof artifact is valid, untampered, and verified under dual hybrid (Classical RSA + NIST FIPS 204 ML-DSA-65) signatures.";
        }

        String verifiedAlgorithm = (proof.getPqcSignature() != null && pqcSignatureValid)
                ? HYBRID_ALGORITHM
                : (proof.getSignatureAlgorithm() != null ? proof.getSignatureAlgorithm() : CLASSICAL_ALGORITHM);

        return ProofVerificationResponseDto.builder()
                .valid(overallValid)
                .signatureValid(signatureValid)
                .pqcSignatureValid(pqcSignatureValid)
                .payloadIntegrityValid(payloadIntegrityValid)
                .merkleInclusionValid(merkleValid)
                .verificationMessage(message)
                .verifiedAt(LocalDateTime.now())
                .verifiedByAlgorithm(verifiedAlgorithm)
                .pqcAlgorithm(proof.getPqcAlgorithm() != null ? proof.getPqcAlgorithm() : PQC_ALGORITHM)
                .build();
    }

    public String buildCanonicalSignPayload(String identifier, LocalDateTime timestamp, String sha256Hash, String merkleRoot) {
        return String.format("IDENTIFIER=%s|TIMESTAMP=%s|HASH=%s|MERKLE_ROOT=%s",
                identifier, timestamp, sha256Hash, merkleRoot);
    }

    private String resolveEntityIdentifier(VerifiableDeletionProofDto proof) {
        if ("VISIT_DELETED".equalsIgnoreCase(proof.getStatus())
                || (proof.getVisitId() != null && !"PATIENT_DELETED".equalsIgnoreCase(proof.getStatus()))) {
            return proof.getVisitId() != null
                    ? proof.getVisitId().toString()
                    : (proof.getPatientRecordId() != null ? proof.getPatientRecordId().toString() : proof.getPatientId());
        } else {
            return proof.getPatientId() != null
                    ? proof.getPatientId()
                    : (proof.getVisitId() != null
                    ? proof.getVisitId().toString()
                    : (proof.getPatientRecordId() != null ? proof.getPatientRecordId().toString() : ""));
        }
    }

    private ProofVerificationResponseDto buildNullProofResponse() {
        return ProofVerificationResponseDto.builder()
                .valid(false)
                .signatureValid(false)
                .pqcSignatureValid(false)
                .payloadIntegrityValid(false)
                .merkleInclusionValid(false)
                .verificationMessage("Proof artifact is null")
                .verifiedAt(LocalDateTime.now())
                .build();
    }

    private String sha256Hex(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
