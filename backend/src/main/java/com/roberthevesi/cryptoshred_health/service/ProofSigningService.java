package com.roberthevesi.cryptoshred_health.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * ProofSigningService — Provides RSA-2048 digital signing and verification
 * for verifiable deletion proof artifacts (GDPR Article 17 compliance).
 */
@Service
@Slf4j
public class ProofSigningService {

    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.keyPair = keyGen.generateKeyPair();
            log.info("Initialized RSA 2048-bit KeyPair for ProofSigningService");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize RSA KeyPairGenerator", e);
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
