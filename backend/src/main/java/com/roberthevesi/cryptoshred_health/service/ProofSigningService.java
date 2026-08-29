package com.roberthevesi.cryptoshred_health.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * ProofSigningService — Provides digital signing and verification for verifiable
 * deletion proof artifacts (GDPR Article 17 compliance).
 *
 * <p>Primary Mode: Vault Transit Asymmetric Signing (RSA-2048 / RSA-3072, SHA2-256, PKCS#1 v1.5)
 * at {@code transit/sign/proof-signing-key} and {@code transit/verify/proof-signing-key}.
 *
 * <p>Fallback Mode: Persisted local RSA-2048 KeyPair on the filesystem when Vault is unavailable
 * (e.g. isolated test environments). Throws {@link IllegalStateException} if neither can be initialized.
 */
@Service
@Slf4j
public class ProofSigningService {

    static {
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(new org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider());
        }
    }

    public static final String KEY_NAME = "proof-signing-key";
    public static final String PQC_ALGORITHM_NAME = "ML-DSA-65 (NIST FIPS 204)";

    @Value("${app.signing.key-dir:backups/keys}")
    private String keyDir = "backups/keys";

    private final VaultOperations vaultOperations;
    private boolean vaultAvailable = false;
    private KeyPair keyPair;
    private PublicKey vaultPublicKey;
    private KeyPair pqcKeyPair;

    @Autowired
    public ProofSigningService(@Autowired(required = false) VaultOperations vaultOperations) {
        this.vaultOperations = vaultOperations;
    }

    public ProofSigningService() {
        this.vaultOperations = null;
    }

    @PostConstruct
    public void init() {
        // Initialize Post-Quantum ML-DSA-65 (Dilithium3) KeyPair
        initPqcKeyPair();

        if (vaultOperations != null) {
            try {
                // Ensure transit secrets engine is mounted
                try {
                    Map<String, VaultMount> mounts = vaultOperations.opsForSys().getMounts();
                    if (mounts != null && !mounts.containsKey("transit/")) {
                        vaultOperations.opsForSys().mount("transit", VaultMount.create("transit"));
                        log.info("Mounted Vault Transit secrets engine at path 'transit/'");
                    }
                } catch (Exception e) {
                    log.debug("Transit mount check: {}", e.getMessage());
                }

                // Ensure asymmetric signing key exists in Vault Transit
                VaultResponse keyResp = vaultOperations.read("transit/keys/" + KEY_NAME);
                if (keyResp == null || keyResp.getData() == null) {
                    vaultOperations.write("transit/keys/" + KEY_NAME, Map.of(
                            "type", "rsa-2048"
                    ));
                    log.info("Created asymmetric RSA-2048 key '{}' in Vault Transit", KEY_NAME);
                    keyResp = vaultOperations.read("transit/keys/" + KEY_NAME);
                }

                if (keyResp != null && keyResp.getData() != null) {
                    extractVaultPublicKey(keyResp.getData());
                    this.vaultAvailable = true;
                    log.info("ProofSigningService initialized with Vault Transit key: {}", KEY_NAME);
                    return;
                }
            } catch (Exception e) {
                log.warn("Vault Transit unavailable for proof signing ({}). Falling back to filesystem RSA keypair.", e.getMessage());
                this.vaultAvailable = false;
            }
        }

        // Fallback: persistent filesystem RSA keypair
        initFilesystemKeyPair();
    }

    private void initPqcKeyPair() {
        try {
            Path dirPath = Paths.get(keyDir != null ? keyDir : "backups/keys");
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path privateKeyPath = dirPath.resolve("pqc_signing_private.key");
            Path publicKeyPath = dirPath.resolve("pqc_signing_public.key");

            if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
                byte[] privBytes = Files.readAllBytes(privateKeyPath);
                byte[] pubBytes = Files.readAllBytes(publicKeyPath);

                KeyFactory keyFactory = KeyFactory.getInstance("Dilithium3", "BCPQC");
                PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubBytes));

                this.pqcKeyPair = new KeyPair(publicKey, privateKey);
                log.info("Loaded persisted ML-DSA-65 (Dilithium3) KeyPair for ProofSigningService from {}", keyDir);
            } else {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Dilithium3", "BCPQC");
                this.pqcKeyPair = keyGen.generateKeyPair();

                Files.write(privateKeyPath, this.pqcKeyPair.getPrivate().getEncoded());
                try {
                    Set<PosixFilePermission> perms = Set.of(
                            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                    Files.setPosixFilePermissions(privateKeyPath, perms);
                } catch (UnsupportedOperationException ignored) {
                    log.warn("Cannot set POSIX permissions on PQC private key file.");
                }
                Files.write(publicKeyPath, this.pqcKeyPair.getPublic().getEncoded());
                log.info("Generated and persisted new ML-DSA-65 (Dilithium3) KeyPair to {}", keyDir);
            }
        } catch (Exception e) {
            log.error("Failed to initialize PQC ML-DSA-65 keypair: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize ProofSigningService PQC keypair", e);
        }
    }

    private void initFilesystemKeyPair() {
        try {
            Path dirPath = Paths.get(keyDir != null ? keyDir : "backups/keys");
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path privateKeyPath = dirPath.resolve("rsa_signing_private.key");
            Path publicKeyPath = dirPath.resolve("rsa_signing_public.key");

            if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
                byte[] privBytes = Files.readAllBytes(privateKeyPath);
                byte[] pubBytes = Files.readAllBytes(publicKeyPath);

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubBytes));

                this.keyPair = new KeyPair(publicKey, privateKey);
                log.info("Loaded persisted RSA 2048-bit KeyPair for ProofSigningService from {}", keyDir);
            } else {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                this.keyPair = keyGen.generateKeyPair();

                Files.write(privateKeyPath, this.keyPair.getPrivate().getEncoded());
                try {
                    Set<PosixFilePermission> perms = Set.of(
                            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                    Files.setPosixFilePermissions(privateKeyPath, perms);
                } catch (UnsupportedOperationException ignored) {
                    log.warn("Cannot set POSIX permissions on private key file. Ensure filesystem-level protection is applied.");
                }
                Files.write(publicKeyPath, this.keyPair.getPublic().getEncoded());
                log.info("Generated and persisted new RSA 2048-bit KeyPair to {}", keyDir);
            }
        } catch (Exception e) {
            log.error("Failed to initialize filesystem RSA keypair: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize ProofSigningService: Vault is unavailable and filesystem key initialization failed", e);
        }
    }

    /**
     * Signs the input data string using Vault Transit asymmetric signing (primary)
     * or fallback SHA256withRSA keypair.
     */
    public String sign(String data) {
        if (vaultAvailable && vaultOperations != null) {
            try {
                Map<String, Object> request = Map.of(
                        "input", Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)),
                        "hash_algorithm", "sha2-256",
                        "signature_algorithm", "pkcs1v15"
                );
                VaultResponse response = vaultOperations.write("transit/sign/" + KEY_NAME, request);
                if (response != null && response.getData() != null && response.getData().containsKey("signature")) {
                    return (String) response.getData().get("signature");
                }
            } catch (Exception e) {
                log.error("Failed to sign proof data via Vault Transit: {}", e.getMessage(), e);
                throw new RuntimeException("Error signing proof artifact via Vault Transit", e);
            }
        }

        if (keyPair == null) {
            throw new IllegalStateException("ProofSigningService is not initialized with a valid signing key");
        }

        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(keyPair.getPrivate());
            rsa.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = rsa.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to sign proof data with fallback key: {}", e.getMessage(), e);
            throw new RuntimeException("Error signing proof artifact", e);
        }
    }

    /**
     * Verifies the signature against input data. Handles both Vault Transit signatures
     * (vault:v1:...) and legacy Base64 RSA signatures.
     */
    public boolean verify(String data, String signature) {
        if (data == null || signature == null || signature.isBlank()) {
            return false;
        }

        // 1. Vault Transit signature (vault:v1:...)
        if (signature.startsWith("vault:")) {
            if (vaultAvailable && vaultOperations != null) {
                try {
                    Map<String, Object> request = Map.of(
                            "input", Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)),
                            "signature", signature,
                            "hash_algorithm", "sha2-256",
                            "signature_algorithm", "pkcs1v15"
                    );
                    VaultResponse response = vaultOperations.write("transit/verify/" + KEY_NAME, request);
                    if (response != null && response.getData() != null) {
                        Object validObj = response.getData().get("valid");
                        if (validObj instanceof Boolean b) {
                            return b;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Vault Transit signature verification failed: {}", e.getMessage());
                }
            }

            // Fallback: verify vault:v1:... signature locally using public key
            try {
                int lastColonIndex = signature.lastIndexOf(':');
                String rawBase64 = (lastColonIndex != -1) ? signature.substring(lastColonIndex + 1) : signature;
                PublicKey pubKey = getPublicKey();
                if (pubKey != null) {
                    Signature rsa = Signature.getInstance("SHA256withRSA");
                    rsa.initVerify(pubKey);
                    rsa.update(data.getBytes(StandardCharsets.UTF_8));
                    byte[] sigBytes = Base64.getDecoder().decode(rawBase64);
                    return rsa.verify(sigBytes);
                }
            } catch (Exception e) {
                log.warn("Local verification of Vault signature failed: {}", e.getMessage());
            }
            return false;
        }

        // 2. Legacy Base64 RSA signature
        try {
            PublicKey pubKey = getPublicKey();
            if (pubKey != null) {
                Signature rsa = Signature.getInstance("SHA256withRSA");
                rsa.initVerify(pubKey);
                rsa.update(data.getBytes(StandardCharsets.UTF_8));
                byte[] signatureBytes = Base64.getDecoder().decode(signature);
                return rsa.verify(signatureBytes);
            }
        } catch (Exception e) {
            log.warn("Legacy RSA signature verification failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Returns the Public Key formatted as X.509 PEM string from Vault or fallback keypair.
     */
    public String getPublicKeyPem() {
        if (vaultAvailable && vaultOperations != null) {
            try {
                VaultResponse keyResp = vaultOperations.read("transit/keys/" + KEY_NAME);
                if (keyResp != null && keyResp.getData() != null) {
                    Object keysObj = keyResp.getData().get("keys");
                    if (keysObj instanceof Map<?, ?> keysMap && !keysMap.isEmpty()) {
                        Object latestVer = keyResp.getData().get("latest_version");
                        Map<?, ?> keyDetail = null;
                        if (latestVer != null) {
                            keyDetail = (Map<?, ?>) keysMap.get(String.valueOf(latestVer));
                        }
                        if (keyDetail == null) {
                            keyDetail = (Map<?, ?>) keysMap.values().iterator().next();
                        }
                        if (keyDetail != null && keyDetail.get("public_key") != null) {
                            return keyDetail.get("public_key").toString().trim();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch public key PEM from Vault Transit: {}", e.getMessage());
            }
        }

        PublicKey pubKey = getPublicKey();
        if (pubKey == null) {
            throw new IllegalStateException("No public key available in ProofSigningService");
        }
        String base64Key = Base64.getEncoder().encodeToString(pubKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64Key.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }

    public PublicKey getPublicKey() {
        if (vaultPublicKey != null) {
            return vaultPublicKey;
        }
        if (keyPair != null) {
            return keyPair.getPublic();
        }
        return null;
    }

    public boolean isVaultAvailable() {
        return vaultAvailable;
    }

    private void extractVaultPublicKey(Map<String, Object> data) {
        try {
            Object keysObj = data.get("keys");
            if (keysObj instanceof Map<?, ?> keysMap && !keysMap.isEmpty()) {
                Object latestVer = data.get("latest_version");
                Map<?, ?> keyDetail = null;
                if (latestVer != null) {
                    keyDetail = (Map<?, ?>) keysMap.get(String.valueOf(latestVer));
                }
                if (keyDetail == null) {
                    keyDetail = (Map<?, ?>) keysMap.values().iterator().next();
                }
                if (keyDetail != null && keyDetail.get("public_key") != null) {
                    String pem = keyDetail.get("public_key").toString();
                    this.vaultPublicKey = parsePublicKeyFromPem(pem);
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse public key from Vault Transit response: {}", e.getMessage());
        }
    }

    private PublicKey parsePublicKeyFromPem(String pem) throws Exception {
        String cleanPem = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }

    /**
     * Signs data using NIST FIPS 204 ML-DSA-65 (CRYSTALS-Dilithium level 3) Post-Quantum signature.
     */
    public String signPqc(String data) {
        if (pqcKeyPair == null) {
            throw new IllegalStateException("ProofSigningService PQC keypair is not initialized");
        }
        try {
            Signature pqcSig = Signature.getInstance("Dilithium3", "BCPQC");
            pqcSig.initSign(pqcKeyPair.getPrivate());
            pqcSig.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = pqcSig.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to generate PQC ML-DSA-65 signature: {}", e.getMessage(), e);
            throw new RuntimeException("Error signing proof artifact with PQC ML-DSA-65", e);
        }
    }

    /**
     * Verifies NIST FIPS 204 ML-DSA-65 Post-Quantum signature against input data.
     */
    public boolean verifyPqc(String data, String signature) {
        return verifyPqc(data, signature, getPqcPublicKey());
    }

    /**
     * Verifies NIST FIPS 204 ML-DSA-65 Post-Quantum signature against input data using a specific public key.
     */
    public boolean verifyPqc(String data, String signature, PublicKey publicKey) {
        if (data == null || signature == null || signature.isBlank() || publicKey == null) {
            return false;
        }
        try {
            Signature pqcSig = Signature.getInstance("Dilithium3", "BCPQC");
            pqcSig.initVerify(publicKey);
            pqcSig.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(signature);
            return pqcSig.verify(sigBytes);
        } catch (Exception e) {
            log.warn("PQC ML-DSA-65 signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    public PublicKey getPqcPublicKey() {
        return pqcKeyPair != null ? pqcKeyPair.getPublic() : null;
    }

    public KeyPair getPqcKeyPair() {
        return pqcKeyPair;
    }

    /**
     * Returns the PQC Public Key formatted as PEM string.
     */
    public String getPqcPublicKeyPem() {
        PublicKey pubKey = getPqcPublicKey();
        if (pubKey == null) {
            return null;
        }
        String base64Key = Base64.getEncoder().encodeToString(pubKey.getEncoded());
        return "-----BEGIN ML-DSA-65 PUBLIC KEY-----\n" +
                base64Key.replaceAll("(.{64})", "$1\n") +
                "\n-----END ML-DSA-65 PUBLIC KEY-----";
    }
}
