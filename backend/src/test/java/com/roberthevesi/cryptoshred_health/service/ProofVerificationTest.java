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
import java.util.Base64;
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

        merkleTreeService = new MerkleTreeService(null);
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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper(), null);

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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper(), null);

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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper(), null);

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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper(), null);

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(proof);

        assertTrue(response.isValid());
        assertTrue(response.isSignatureValid());
        assertTrue(response.isPayloadIntegrityValid());
        assertTrue(response.isMerkleInclusionValid());
    }

    @Test
    void testMultiLeafMerkleTreeInclusion() {
        MerkleTreeService service = new MerkleTreeService(null);
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

        ErasureService erasureService = new ErasureService(null, null, null, null, null, null, null, proofSigningService, merkleTreeService, new com.fasterxml.jackson.databind.ObjectMapper(), null);

        ProofVerificationResponseDto response = erasureService.verifyProofArtifact(proof);

        assertTrue(response.isValid());
        assertTrue(response.isSignatureValid());
        assertTrue(response.isPayloadIntegrityValid());
        assertTrue(response.isMerkleInclusionValid());
    }

    // =========================================================================
    // Phase 1: EnvelopeEncryptionService AAD Tests
    // =========================================================================

    @Test
    void testEnvelopeEncryptionServiceAadMatchAndMismatch() {
        EnvelopeEncryptionService envelopeService = new EnvelopeEncryptionService();
        byte[] dek = envelopeService.generateDek();
        byte[] plaintext = "Patient Medical Record 101".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "PAT-10001".getBytes(StandardCharsets.UTF_8);
        byte[] wrongAad = "PAT-99999".getBytes(StandardCharsets.UTF_8);

        // 1. Encrypt and Decrypt with matching AAD
        EnvelopeEncryptionService.EncryptedPayload payload = envelopeService.encrypt(plaintext, dek, aad);
        assertNotNull(payload.ciphertextBase64());
        assertNotNull(payload.ivBase64());

        byte[] decrypted = envelopeService.decrypt(payload.ciphertextBase64(), payload.ivBase64(), dek, aad);
        assertArrayEquals(plaintext, decrypted);

        // 2. Decrypt with wrong AAD fails
        assertThrows(IllegalStateException.class, () ->
                envelopeService.decrypt(payload.ciphertextBase64(), payload.ivBase64(), dek, wrongAad));

        // 3. Backward compatible 2-arg and 3-arg without AAD
        EnvelopeEncryptionService.EncryptedPayload legacyPayload = envelopeService.encrypt(plaintext, dek);
        byte[] legacyDecrypted = envelopeService.decrypt(legacyPayload.ciphertextBase64(), legacyPayload.ivBase64(), dek);
        assertArrayEquals(plaintext, legacyDecrypted);
    }

    // =========================================================================
    // Phase 1: ProofSigningService Vault Transit & Fallback Tests
    // =========================================================================

    @Test
    void testProofSigningServiceVaultTransitMode() throws Exception {
        org.springframework.vault.core.VaultOperations vaultOps = Mockito.mock(org.springframework.vault.core.VaultOperations.class, Mockito.RETURNS_DEEP_STUBS);

        java.security.KeyPairGenerator keyGen = java.security.KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        java.security.KeyPair mockKp = keyGen.generateKeyPair();
        String validPem = "-----BEGIN PUBLIC KEY-----\n" +
                java.util.Base64.getEncoder().encodeToString(mockKp.getPublic().getEncoded()).replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";

        org.springframework.vault.support.VaultResponse keyResp = Mockito.mock(org.springframework.vault.support.VaultResponse.class);
        Mockito.when(keyResp.getData()).thenReturn(Map.of(
                "type", "rsa-2048",
                "latest_version", 1,
                "keys", Map.of(
                        "1", Map.of("public_key", validPem)
                )
        ));
        Mockito.when(vaultOps.read("transit/keys/" + ProofSigningService.KEY_NAME)).thenReturn(keyResp);

        org.springframework.vault.support.VaultResponse signResp = Mockito.mock(org.springframework.vault.support.VaultResponse.class);
        Mockito.when(signResp.getData()).thenReturn(Map.of("signature", "vault:v1:MEQCIDaRandomVaultSignatureBase64String=="));
        Mockito.when(vaultOps.write(Mockito.eq("transit/sign/" + ProofSigningService.KEY_NAME), Mockito.anyMap())).thenReturn(signResp);

        org.springframework.vault.support.VaultResponse verifyResp = Mockito.mock(org.springframework.vault.support.VaultResponse.class);
        Mockito.when(verifyResp.getData()).thenReturn(Map.of("valid", true));
        Mockito.when(vaultOps.write(Mockito.eq("transit/verify/" + ProofSigningService.KEY_NAME), Mockito.anyMap())).thenReturn(verifyResp);

        ProofSigningService vaultSigningService = new ProofSigningService(vaultOps);
        vaultSigningService.init();

        assertTrue(vaultSigningService.isVaultAvailable());

        String data = "IDENTIFIER=PAT-001|TIMESTAMP=2026-08-28T21:00:00|HASH=abcdef";
        String signature = vaultSigningService.sign(data);
        assertEquals("vault:v1:MEQCIDaRandomVaultSignatureBase64String==", signature);

        assertTrue(vaultSigningService.verify(data, signature));
        assertTrue(vaultSigningService.getPublicKeyPem().contains("BEGIN PUBLIC KEY"));
    }

    @Test
    void testProofSigningServiceVerifyBothVaultAndLegacySignatures() {
        String data = "CANONICAL_TEST_DATA";

        // Legacy Base64 signature
        String legacySig = proofSigningService.sign(data);
        assertFalse(legacySig.startsWith("vault:"));
        assertTrue(proofSigningService.verify(data, legacySig));

        // Vault-formatted signature wrapping the base64 signature
        String vaultSig = "vault:v1:" + legacySig;
        assertTrue(proofSigningService.verify(data, vaultSig));

        // Tampered data should fail
        assertFalse(proofSigningService.verify(data + "_tampered", legacySig));
        assertFalse(proofSigningService.verify(data + "_tampered", vaultSig));
    }

    // =========================================================================
    // Phase 3: MerkleTreeService Directional Proofs & Compatibility Tests
    // =========================================================================

    @Test
    void testMerkleTreeDirectionalProofsAndLegacyCompatibility() {
        MerkleTreeService service = new MerkleTreeService(null);
        service.addLeaf("leaf_A");
        service.addLeaf("leaf_B");
        service.addLeaf("leaf_C");
        service.addLeaf("leaf_D");

        String root = service.getMerkleRoot();

        // Check directional prefixes
        List<String> proofA = service.getInclusionProof("leaf_A");
        assertFalse(proofA.isEmpty());
        assertTrue(proofA.get(0).startsWith("L:"), "leaf_A is at index 0 (even), sibling proof step should have L: prefix");

        List<String> proofB = service.getInclusionProof("leaf_B");
        assertFalse(proofB.isEmpty());
        assertTrue(proofB.get(0).startsWith("R:"), "leaf_B is at index 1 (odd), sibling proof step should have R: prefix");

        // Verify directional proofs
        assertTrue(service.verifyInclusion("leaf_A", proofA, root));
        assertTrue(service.verifyInclusion("leaf_B", proofB, root));

        // Verify legacy proof without L:/R: prefix
        List<String> legacyProof = List.of("leaf_B", proofA.get(1).replace("L:", "").replace("R:", ""));
        assertTrue(service.verifyInclusion("leaf_A", legacyProof, root));
    }
}
