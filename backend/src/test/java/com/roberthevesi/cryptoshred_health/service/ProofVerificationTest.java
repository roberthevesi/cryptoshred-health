package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.ProofVerificationResponseDto;
import com.roberthevesi.cryptoshred_health.dto.VerifiableDeletionProofDto;
import com.roberthevesi.cryptoshred_health.repository.MerkleNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProofVerificationTest {

    private ProofSigningService proofSigningService;
    private MerkleTreeService merkleTreeService;
    private MerkleNodeRepository merkleNodeRepository;

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

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
        String sha256Hash = sha256(auditTrail);

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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper());

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
        String sha256Hash = sha256(auditTrail);

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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper());

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(tamperedProof);

        assertFalse(response.isValid());
        assertFalse(response.isPayloadIntegrityValid());
    }

    @Test
    void testSignAndVerifyVisitProofWithPatientIdPopulated() {
        UUID visitId = UUID.randomUUID();
        String patientId = "PAT-10001";
        LocalDateTime now = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_VISIT|VISIT_ID=" + visitId + "|REQUESTED_BY=auditor@health.gov|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=" + now;
        String sha256Hash = sha256(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = "IDENTIFIER=" + visitId + "|TIMESTAMP=" + now + "|HASH=" + sha256Hash + "|MERKLE_ROOT=" + merkleRoot;
        String signature = proofSigningService.sign(canonicalPayload);

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .visitId(visitId)
                .patientId(patientId) // patientId is populated alongside visitId
                .vaultKeyName("patient_key_visit_" + visitId)
                .requestedBy("auditor@health.gov")
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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper());

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(proof);

        assertTrue(response.isValid());
        assertTrue(response.isSignatureValid());
        assertTrue(response.isPayloadIntegrityValid());
        assertTrue(response.isMerkleInclusionValid());
    }

    @Test
    void testSignAndVerifyPatientProof() {
        String patientId = "PAT-10001";
        LocalDateTime now = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_PATIENT|PATIENT_ID=" + patientId + "|VISITS_COUNT=3|REQUESTED_BY=auditor@health.gov|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=" + now;
        String sha256Hash = sha256(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = "IDENTIFIER=" + patientId + "|TIMESTAMP=" + now + "|HASH=" + sha256Hash + "|MERKLE_ROOT=" + merkleRoot;
        String signature = proofSigningService.sign(canonicalPayload);

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .patientId(patientId)
                .vaultKeyName("patient_" + patientId)
                .requestedBy("auditor@health.gov")
                .timestamp(now)
                .status("PATIENT_DELETED")
                .coveredStorageLayers(List.of("POSTGRES_DB", "KAFKA_EVENT_LOG", "REDIS_CACHE", "WORM_BACKUP"))
                .layerStatus(Map.of("POSTGRES_DB", "DEMOGRAPHICS_AND_VISITS_NULLIFIED"))
                .auditTrail(auditTrail)
                .auditTrailHash(sha256Hash)
                .merkleRoot(merkleRoot)
                .merklePath(merklePath)
                .signatureAlgorithm("SHA256withRSA")
                .digitalSignature(signature)
                .build();

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper());

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(proof);

        assertTrue(response.isValid());
        assertTrue(response.isSignatureValid());
        assertTrue(response.isPayloadIntegrityValid());
        assertTrue(response.isMerkleInclusionValid());
    }

    @Test
    void testMultiLeafMerkleTreeInclusion() {
        MerkleTreeService service = new MerkleTreeService(merkleNodeRepository);
        List<String> leafHashes = List.of(
                sha256("leaf_hash_0"),
                sha256("leaf_hash_1"),
                sha256("leaf_hash_2"),
                sha256("leaf_hash_3"),
                sha256("leaf_hash_4")
        );

        for (String leaf : leafHashes) {
            service.addLeaf(leaf);
        }

        String root = service.getMerkleRoot();
        assertNotNull(root);

        // Verify inclusion proof for every single leaf
        for (String leaf : leafHashes) {
            List<String> proof = service.getInclusionProof(leaf);
            assertTrue(service.verifyInclusion(leaf, proof, root),
                    "Inclusion verification should succeed for leaf: " + leaf);
        }
    }

    @Test
    void testScopedProofVerification() {
        UUID visitId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        String auditTrail = "ACTION=CRYPTO_SHRED_VISIT|VISIT_ID=" + visitId + "|REQUESTED_BY=auditor@health.gov|STORAGE_LAYERS=POSTGRES_DB,KAFKA_EVENT_LOG,REDIS_CACHE,WORM_BACKUP|TIMESTAMP=" + now;
        String sha256Hash = sha256(auditTrail);

        merkleTreeService.addLeaf(sha256Hash);
        String merkleRoot = merkleTreeService.getMerkleRoot();
        List<String> merklePath = merkleTreeService.getInclusionProof(sha256Hash);

        String canonicalPayload = "IDENTIFIER=" + visitId + "|TIMESTAMP=" + now + "|HASH=" + sha256Hash + "|MERKLE_ROOT=" + merkleRoot;
        String signature = proofSigningService.sign(canonicalPayload);

        VerifiableDeletionProofDto proof = VerifiableDeletionProofDto.builder()
                .proofVersion("1.0")
                .scope("CLINICAL_VISIT")
                .entityDescription("Clinical Visit Chart: " + visitId + " (Diagnosis: Cardiology)")
                .visitId(visitId)
                .patientId("PAT-999")
                .vaultKeyName("vault-key-" + visitId)
                .requestedBy("auditor@health.gov")
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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper());

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(proof);

        assertTrue(response.isValid());
        assertTrue(response.isSignatureValid());
        assertTrue(response.isPayloadIntegrityValid());
        assertTrue(response.isMerkleInclusionValid());
    }
}
