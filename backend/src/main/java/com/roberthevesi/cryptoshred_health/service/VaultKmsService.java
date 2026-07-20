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

    /** Wraps (encrypts) a raw DEK byte array using Vault Transit KEK. */
    public String wrapDek(String keyName, byte[] rawDek) {
        ensureKeyExists(keyName);
        String base64Dek = Base64.getEncoder().encodeToString(rawDek);
        Ciphertext ciphertext = vaultOperations.opsForTransit().encrypt(keyName, Plaintext.of(base64Dek));
        return ciphertext.getCiphertext();
    }

    /** Unwraps (decrypts) a wrapped DEK string using Vault Transit KEK. */
    public byte[] unwrapDek(String keyName, String wrappedDek) {
        Plaintext plaintext = vaultOperations.opsForTransit().decrypt(keyName, Ciphertext.of(wrappedDek));
        return Base64.getDecoder().decode(plaintext.asString());
    }

    /** Permanently destroys (deletes) the KEK from Vault Transit Engine. */
    public void destroyKey(String keyName) {
        try {
            vaultOperations.write("transit/keys/" + keyName + "/config", Map.of("deletion_allowed", true));
            vaultOperations.opsForTransit().deleteKey(keyName);
            log.info("Vault Transit KEK {} permanently destroyed", keyName);
        } catch (Exception e) {
            log.error("Failed to destroy Vault key {}", keyName, e);
            throw new IllegalStateException("Failed to destroy KMS key in Vault: " + keyName, e);
        }
    }
}
