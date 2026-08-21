package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.ProofVerificationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProofVerificationTest {

    private ProofSigningService proofSigningService;
    private MerkleTreeService merkleTreeService;
    private MerkleNodeRepository merkleNodeRepository;

    @BeforeEach
    void setUp() {
        proofSigningService = new ProofSigningService();
        proofSigningService.init();

        merkleNodeRepository = Mockito.mock(MerkleNodeRepository.class);
        merkleTreeService = new MerkleTreeService(merkleNodeRepository);
    }

    @Test
    void testSignAndVerifyProofArtifact() {
        UUID visitId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_VISIT|VISIT_ID=" + visitId + "|REQUESTED_BY=auditor_test|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=" + now;
        String sha256Hash = MerkleTreeService.sha256(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = "IDENTIFIER=" + visitId + "|TIMESTAMP=" + now + "|HASH=" + sha256Hash + "|MERKLE_ROOT=" + merkleRoot;
        String signature = proofSigningService.sign(canonicalPayload);

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .visitId(visitId)
                .vaultKeyName("vault-key-" + visitId)
                .requestedBy("auditor_test")
                .timestamp(now)
                .status("VISIT_DELETED")
                .coveredStorageLayers(List.of("POSTGRES_DB", "KAFKA_EVENT_LOG", "REDIS_CACHE", "WORM_BACKUP"))
                .layerStatus(Map.of("POSTGRES_DB", "TEXT_NULLIFIED"))
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(signature)
                .build();

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, proofSigningService, merkleTreeService);

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(proof);

        assertTrue(response.isValid());
        assertTrue(response.isSignatureValid());
        assertTrue(response.isPayloadIntegrityValid());
        assertTrue(response.isMerkleInclusionValid());
    }

    @Test
    void testTamperedPayloadFailsVerification() {
        UUID visitId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_VISIT|VISIT_ID=" + visitId + "|REQUESTED_BY=auditor_test|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=" + now;
        String sha256Hash = MerkleTreeService.sha256(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = "IDENTIFIER=" + visitId + "|TIMESTAMP=" + now + "|HASH=" + sha256Hash + "|MERKLE_ROOT=" + merkleRoot;
        String signature = proofSigningService.sign(canonicalPayload);

        // Tamper with auditTrail
        VerifiableDeletionProofDto tamperedProof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .visitId(visitId)
                .vaultKeyName("vault-key-" + visitId)
                .requestedBy("auditor_test")
                .timestamp(now)
                .status("VISIT_DELETED")
                .auditTrail(auditTrail + " [TAMPERED]")
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(signature)
                .build();

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, proofSigningService, merkleTreeService);

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(tamperedProof);

        assertFalse(response.isValid());
        assertFalse(response.isPayloadIntegrityValid());
    }
}
