package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.ProofVerificationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProofVerificationTest {

    private ProofSigningService proofSigningService;
    private MerkleTreeService merkleTreeService;

    @BeforeEach
    void setUp() {
        proofSigningService = new ProofSigningService();
        proofSigningService.init();
        merkleTreeService = new MerkleTreeService();
    }

    @Test
    void testSignAndVerifyProofArtifact() {
        UUID recordId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED|RECORD_ID=" + recordId + "|REQUESTED_BY=auditor_test|TIMESTAMP=" + now;
        String sha256Hash = MerkleTreeService.sha256(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = "RECORD_ID=" + recordId + "|TIMESTAMP=" + now + "|HASH=" + sha256Hash + "|MERKLE_ROOT=" + merkleRoot;
        String signature = proofSigningService.sign(canonicalPayload);

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .patientRecordId(recordId)
                .vaultKeyName("vault-key-" + recordId)
                .requestedBy("auditor_test")
                .timestamp(now)
                .status("DELETED")
                .coveredStorageLayers(List.of("POSTGRES_DB", "KAFKA_EVENT_LOG", "REDIS_CACHE", "WORM_BACKUP"))
                .layerStatus(Map.of("POSTGRES_DB", "TEXT_NULLIFIED"))
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(signature)
                .build();

        ErasureService erasureService = new ErasureService(null, null, null, null, null, proofSigningService, merkleTreeService);

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(proof);

        assertTrue(response.isValid());
        assertTrue(response.isSignatureValid());
        assertTrue(response.isPayloadIntegrityValid());
        assertTrue(response.isMerkleInclusionValid());
    }

    @Test
    void testTamperedPayloadFailsVerification() {
        UUID recordId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED|RECORD_ID=" + recordId + "|REQUESTED_BY=auditor_test|TIMESTAMP=" + now;
        String sha256Hash = MerkleTreeService.sha256(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = "RECORD_ID=" + recordId + "|TIMESTAMP=" + now + "|HASH=" + sha256Hash + "|MERKLE_ROOT=" + merkleRoot;
        String signature = proofSigningService.sign(canonicalPayload);

        // Tamper with auditTrail
        VerifiableDeletionProofDto tamperedProof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .patientRecordId(recordId)
                .vaultKeyName("vault-key-" + recordId)
                .requestedBy("auditor_test")
                .timestamp(now)
                .status("DELETED")
                .auditTrail(auditTrail + " [TAMPERED]")
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(signature)
                .build();

        ErasureService erasureService = new ErasureService(null, null, null, null, null, proofSigningService, merkleTreeService);

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(tamperedProof);

        assertFalse(response.isValid());
        assertFalse(response.isPayloadIntegrityValid());
    }
}
