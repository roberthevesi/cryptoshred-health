package com.roberthevesi.cryptoshred_health.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * ProofSigningService — Provides RSA-2048 digital signing and verification
 * for verifiable deletion proof artifacts (GDPR Article 17 compliance).
 * Backed by persistent key storage to guarantee signature verification survives server restarts.
 */
@Service
@Slf4j
public class ProofSigningService {

    @Value("${app.signing.key-dir:backups/keys}")
    private String keyDir;

    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        try {
            Path dirPath = Paths.get(keyDir);
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
            log.warn("Could not load/save persisted RSA keypair, falling back to in-memory: {}", e.getMessage());
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                this.keyPair = keyGen.generateKeyPair();
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("RSA not available", ex);
            }
        }
    }

    /**
     * Signs the input string using SHA256withRSA and returns Base64 encoded signature.
     */
    public String sign(String data) {
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(keyPair.getPrivate());
            rsa.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = rsa.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to sign proof data: {}", e.getMessage());
            throw new RuntimeException("Error signing proof artifact", e);
        }
    }

    /**
     * Verifies the Base64 signature against data using active public key.
     */
    public boolean verify(String data, String base64Signature) {
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(keyPair.getPublic());
            rsa.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
            return rsa.verify(signatureBytes);
        } catch (Exception e) {
            log.warn("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns the Public Key formatted as X.509 PEM string.
     */
    public String getPublicKeyPem() {
        String base64Key = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                base64Key.replaceAll("(.{64})", "$1\n") +
                "\n-----END PUBLIC KEY-----";
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }
}
