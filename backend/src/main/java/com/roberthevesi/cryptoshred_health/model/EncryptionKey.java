package com.roberthevesi.cryptoshred_health.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents an AES envelope encryption key associated with a PatientRecord.
 * Stores the Vault wrapped DEK, IV, and Vault key reference name.
 * Crypto-shredding is achieved by destroying the key in Vault and nullifying wrappedDek.
 */
@Entity
@Table(name = "encryption_keys")
@Getter
@Setter
@NoArgsConstructor
public class EncryptionKey {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    public void ensureId() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }


    /** Logical identifier for the key, referenced by PatientRecord. */
    @Column(unique = true, nullable = false)
    private String keyId;

    /** The Vault Transit KEK reference name (e.g., patient_{id}). */
    @Column
    private String vaultKeyName;

    /** Wrapped Data Encryption Key (DEK) via Vault Transit KEK. */
    @Column(columnDefinition = "TEXT")
    private String wrappedDek;

    /** Initialization Vector (IV) for AES-GCM decryption (Base64). */
    @Column
    private String iv;

    /** Legacy / raw key value fallback. Nullified upon shredding. */
    @Column
    private String keyValue;

    @Column(nullable = false)
    private boolean invalidated = false;

    private LocalDateTime invalidatedAt;

    private LocalDateTime rotatedAt;

    @Column(nullable = false)
    private int keyVersion = 1;

    public EncryptionKey(String keyId, String keyValue) {
        this.keyId = keyId;
        this.keyValue = keyValue;
    }

    public EncryptionKey(String keyId, String vaultKeyName, String wrappedDek, String iv) {
        this.keyId = keyId;
        this.vaultKeyName = vaultKeyName;
        this.wrappedDek = wrappedDek;
        this.iv = iv;
    }
}


