package com.roberthevesi.cryptoshred_health.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.VaultMount;

import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultKmsService {

    private final VaultOperations vaultOperations;

    @PostConstruct
    public void initTransitEngine() {
        try {
            Map<String, VaultMount> mounts = vaultOperations.opsForSys().getMounts();
            if (!mounts.containsKey("transit/")) {
                vaultOperations.opsForSys().mount("transit", VaultMount.create("transit"));
                log.info("Mounted Vault Transit secrets engine at path 'transit/'");
            }
        } catch (Exception e) {
            log.warn("Vault Transit engine initialization check: {}", e.getMessage());
        }
    }

    /** Ensures a Transit key exists in Vault for the given key name. */
    public void ensureKeyExists(String keyName) {
        try {
            vaultOperations.opsForTransit().createKey(keyName);
            vaultOperations.write("transit/keys/" + keyName + "/config", Map.of("deletion_allowed", true));
            log.info("Transit key {} initialized in Vault", keyName);
        } catch (Exception e) {
            log.debug("Transit key {} initialization: {}", keyName, e.getMessage());
        }
    }

    /** Checks if a named Transit key exists in Vault. */
    public boolean keyExists(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            return false;
        }
        try {
            org.springframework.vault.support.VaultResponse response = vaultOperations.read("transit/keys/" + keyName);
            return response != null && response.getData() != null && !response.getData().isEmpty();
        } catch (Exception e) {
            log.debug("Transit key {} does not exist in Vault: {}", keyName, e.getMessage());
            return false;
        }
    }

    /** Wraps (encrypts) a raw DEK byte array using Vault Transit KEK. */
    public String wrapDek(String keyName, byte[] rawDek) {
        String base64Dek = Base64.getEncoder().encodeToString(rawDek);
        Ciphertext ciphertext = vaultOperations.opsForTransit().encrypt(keyName, Plaintext.of(base64Dek));
        return ciphertext.getCiphertext();
    }

    /** Unwraps (decrypts) a wrapped DEK string using Vault Transit KEK. */
    public byte[] unwrapDek(String keyName, String wrappedDek) {
        try {
            Plaintext plaintext = vaultOperations.opsForTransit().decrypt(keyName, Ciphertext.of(wrappedDek));
            if (plaintext == null || plaintext.asString() == null) {
                throw new IllegalStateException("Vault returned null plaintext for key: " + keyName);
            }
            return Base64.getDecoder().decode(plaintext.asString());
        } catch (Exception e) {
            log.warn("Vault Transit unwrap failed for key {}: {}", keyName, e.getMessage());
            throw new IllegalStateException("Vault Transit KEK missing or invalid: " + keyName, e);
        }
    }

    /** Permanently destroys (deletes) the KEK from Vault Transit Engine. Idempotent. */
    public void destroyKey(String keyName) {
        try {
            vaultOperations.write("transit/keys/" + keyName + "/config", Map.of("deletion_allowed", true));
            vaultOperations.opsForTransit().deleteKey(keyName);
            log.info("Vault Transit KEK {} permanently destroyed", keyName);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("no existing key") || msg.contains("not found") || msg.contains("Status 400"))) {
                log.info("Vault Transit KEK {} was already destroyed or not found in Vault", keyName);
                return;
            }
            log.error("Failed to destroy Vault key {}", keyName, e);
            throw new IllegalStateException("Failed to destroy KMS key in Vault: " + keyName, e);
        }
    }

    /**
     * Rotates the named Vault Transit KEK to a new cryptographic key version (e.g. v1 -> v2).
     */
    public void rotateKey(String keyName) {
        try {
            vaultOperations.opsForTransit().rotate(keyName);
            log.info("Vault Transit KEK {} rotated to new version", keyName);
        } catch (Exception e) {
            log.error("Failed to rotate Vault key {}", keyName, e);
            throw new IllegalStateException("Failed to rotate KMS key in Vault: " + keyName, e);
        }
    }

    /**
     * Cryptographically re-wraps an existing wrapped DEK under the latest KEK version.
     * The DEK is never decrypted or exposed outside HashiCorp Vault.
     */
    public String rewrapDek(String keyName, String wrappedDek) {
        try {
            String rewrapped = vaultOperations.opsForTransit().rewrap(keyName, wrappedDek);
            if (rewrapped == null || rewrapped.isBlank()) {
                throw new IllegalStateException("Vault returned null or blank rewrapped ciphertext for key: " + keyName);
            }
            log.info("Vault Transit DEK successfully rewrapped under latest KEK version for key: {}", keyName);
            return rewrapped;
        } catch (Exception e) {
            log.error("Failed to rewrap DEK for key {}: {}", keyName, e.getMessage());
            throw new IllegalStateException("Vault Transit DEK rewrap failed for key: " + keyName, e);
        }
    }
}


