package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.ProofVerificationDto;
import com.roberthevesi.cryptoshred_health.dto.ProofVerificationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PostQuantumProofVerificationTest {

    @TempDir
    Path tempDir;

    private ProofSigningService proofSigningService;
    private MerkleTreeService merkleTreeService;
    private ProofVerificationService proofVerificationService;

    @BeforeEach
    void setUp() {
        proofSigningService = new ProofSigningService();
        // Set temp directory for signing keys to avoid polluting repo during isolated testing
        try {
            var field = ProofSigningService.class.getDeclaredField("keyDir");
            field.setAccessible(true);
            field.set(proofSigningService, tempDir.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        proofSigningService.init();

        merkleTreeService = mock(MerkleTreeService.class);
        when(merkleTreeService.verifyInclusion(any(), any(), any())).thenReturn(true);

        proofVerificationService = new ProofVerificationService(proofSigningService, merkleTreeService);
    }

    @Test
    @DisplayName("PQC ML-DSA-65 (NIST FIPS 204) signature generation and verification succeeds")
    void testPqcSignAndVerify() {
        String testData = "ACTION=CRYPTO_SHRED|PATIENT_ID=PAT-12345678|HASH=abcd1234efgh5678";

        String pqcSig = proofVerificationService.signPostQuantum(testData);
        assertNotNull(pqcSig, "PQC signature must not be null");
        assertFalse(pqcSig.isBlank(), "PQC signature must not be blank");

        boolean verified = proofVerificationService.verifyPostQuantum(testData, pqcSig);
        assertTrue(verified, "PQC ML-DSA-65 signature must verify successfully with authentic data");

        boolean tamperedVerify = proofVerificationService.verifyPostQuantum(testData + "_TAMPERED", pqcSig);
        assertFalse(tamperedVerify, "PQC ML-DSA-65 signature must fail when data is tampered");
    }

    @Test
    @DisplayName("Classical RSA-2048 signature generation and verification succeeds")
    void testClassicalSignAndVerify() {
        String testData = "ACTION=CRYPTO_SHRED|PATIENT_ID=PAT-12345678|HASH=abcd1234efgh5678";

        String rsaSig = proofVerificationService.signClassical(testData);
        assertNotNull(rsaSig, "Classical RSA signature must not be null");

        boolean verified = proofVerificationService.verifyClassical(testData, rsaSig);
        assertTrue(verified, "Classical RSA signature must verify successfully");

        boolean tamperedVerify = proofVerificationService.verifyClassical(testData + "_TAMPERED", rsaSig);
        assertFalse(tamperedVerify, "Classical RSA signature must fail when data is tampered");
    }

    @Test
    @DisplayName("Hybrid Dual-Signed Verifiable Deletion Proof passes all checks")
    void testHybridProofVerificationValid() {
        String patientId = "PAT-XYZ9988";
        LocalDateTime timestamp = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_PATIENT|PATIENT_ID=" + patientId + "|STORAGE_LAYERS=POSTGRES_DB,KAFKA,REDIS,WORM|TIMESTAMP=" + timestamp;

        // SHA-256 of audit trail
        String sha256Hash;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(auditTrail.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            sha256Hash = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String merkleRoot = "root_abc123";
        List<String> merklePath = List.of("path_1", "path_2");

        String canonicalPayload = proofVerificationService.buildCanonicalSignPayload(patientId, timestamp, sha256Hash, merkleRoot);
        String rsaSig = proofVerificationService.signClassical(canonicalPayload);
        String pqcSig = proofVerificationService.signPostQuantum(canonicalPayload);

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .scope("PATIENT_PROFILE")
                .patientId(patientId)
                .vaultKeyName("patient_key_123")
                .requestedBy("dpo@hospital.nhs.uk")
                .timestamp(timestamp)
                .status("PATIENT_DELETED")
                .coveredStorageLayers(List.of("POSTGRES_DB", "KAFKA_EVENT_LOG", "REDIS_CACHE", "WORM_BACKUP"))
                .layerStatus(Map.of("POSTGRES_DB", "DEMOGRAPHICS_NULLIFIED"))
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(rsaSig)
                .pqcAlgorithm("ML-DSA-65 (NIST FIPS 204)")
                .pqcSignature(pqcSig)
                .build();

        ProofVerificationResponseDto response = proofVerificationService.verifyProofArtifact(proof);

        assertTrue(response.isValid(), "Overall verification must be valid");
        assertTrue(response.isPayloadIntegrityValid(), "Payload integrity must be valid");
        assertTrue(response.isSignatureValid(), "Classical RSA signature must be valid");
        assertTrue(response.isPqcSignatureValid(), "Post-Quantum ML-DSA-65 signature must be valid");
        assertTrue(response.isMerkleInclusionValid(), "Merkle inclusion must be valid");
        assertEquals("Hybrid RSA-2048 + ML-DSA-65 (NIST FIPS 204)", response.getVerifiedByAlgorithm());
        assertEquals("ML-DSA-65 (NIST FIPS 204)", response.getPqcAlgorithm());

        // Also test ProofVerificationDto conversion and verification
        ProofVerificationDto proofVerificationDto = ProofVerificationDto.from(proof);
        ProofVerificationResponseDto dtoResp = proofVerificationService.verifyProofArtifact(proofVerificationDto);
        assertTrue(dtoResp.isValid());
        assertTrue(dtoResp.isPqcSignatureValid());
    }

    @Test
    @DisplayName("Verification fails if audit trail payload is tampered with")
    void testTamperedPayloadFails() {
        String patientId = "PAT-XYZ9988";
        LocalDateTime timestamp = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_PATIENT|PATIENT_ID=" + patientId;
        String sha256Hash = "invalid_hash_1234567890abcdef";

        String canonicalPayload = proofVerificationService.buildCanonicalSignPayload(patientId, timestamp, sha256Hash, "root");
        String rsaSig = proofVerificationService.signClassical(canonicalPayload);
        String pqcSig = proofVerificationService.signPostQuantum(canonicalPayload);

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .patientId(patientId)
                .timestamp(timestamp)
                .status("PATIENT_DELETED")
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .digitalSignature(rsaSig)
                .pqcSignature(pqcSig)
                .build();

        ProofVerificationResponseDto response = proofVerificationService.verifyProofArtifact(proof);

        assertFalse(response.isValid(), "Verification must fail when payload is tampered");
        assertFalse(response.isPayloadIntegrityValid(), "Payload integrity must be invalid");
        assertTrue(response.getVerificationMessage().contains("tampered"));
    }

    @Test
    @DisplayName("Verification fails if PQC ML-DSA-65 signature is forged or corrupted")
    void testForgedPqcSignatureFails() {
        String patientId = "PAT-XYZ9988";
        LocalDateTime timestamp = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_PATIENT|PATIENT_ID=" + patientId;

        String sha256Hash;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(auditTrail.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            sha256Hash = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String canonicalPayload = proofVerificationService.buildCanonicalSignPayload(patientId, timestamp, sha256Hash, null);
        String rsaSig = proofVerificationService.signClassical(canonicalPayload);
        String corruptedPqcSig = java.util.Base64.getEncoder().encodeToString("corrupted_pqc_signature_bytes".getBytes());

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .patientId(patientId)
                .timestamp(timestamp)
                .status("PATIENT_DELETED")
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .digitalSignature(rsaSig)
                .pqcSignature(corruptedPqcSig)
                .build();

        ProofVerificationResponseDto response = proofVerificationService.verifyProofArtifact(proof);

        assertFalse(response.isValid(), "Overall verification must fail on corrupted PQC signature");
        assertTrue(response.isSignatureValid(), "Classical RSA signature should still be valid");
        assertFalse(response.isPqcSignatureValid(), "PQC ML-DSA-65 signature must fail");
    }
}
