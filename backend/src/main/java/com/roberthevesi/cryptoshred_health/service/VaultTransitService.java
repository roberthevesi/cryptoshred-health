package com.roberthevesi.cryptoshred_health.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * VaultTransitService — Facade service for HashiCorp Vault Transit engine operations
 * (KEK creation, wrapping, unwrapping, key existence check, rotation, rewrapping, and permanent destruction).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultTransitService {

    private final VaultKmsService vaultKmsService;

    public void ensureKeyExists(String keyName) {
        vaultKmsService.ensureKeyExists(keyName);
    }

    public boolean keyExists(String keyName) {
        return vaultKmsService.keyExists(keyName);
    }

    public String wrapDek(String keyName, byte[] rawDek) {
        return vaultKmsService.wrapDek(keyName, rawDek);
    }

    public byte[] unwrapDek(String keyName, String wrappedDek) {
        return vaultKmsService.unwrapDek(keyName, wrappedDek);
    }

    public void destroyKey(String keyName) {
        vaultKmsService.destroyKey(keyName);
    }

    public void rotateKey(String keyName) {
        vaultKmsService.rotateKey(keyName);
    }

    public String rewrapDek(String keyName, String wrappedDek) {
        return vaultKmsService.rewrapDek(keyName, wrappedDek);
    }

    public java.util.List<String> listKeys() {
        return vaultKmsService.listKeys();
    }
}
