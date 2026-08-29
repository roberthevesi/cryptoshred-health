package com.roberthevesi.cryptoshred_health.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * CryptoService — Cryptographic engine providing AES-256-GCM envelope encryption,
 * HMAC-SHA256 blind indexing, and strict memory zeroization of raw DEK byte arrays.
 */
@Service
@Slf4j
public class CryptoService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes
    private static final int DEK_SIZE_BYTES = 32;  // 256 bits

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.crypto.blind-index-salt:${BLIND_INDEX_SALT:}}")
    private String configuredBlindIndexSalt;

    public record EncryptedPayload(String ciphertextBase64, String ivBase64) {}

    /**
     * Generates a cryptographically strong random 256-bit DEK.
     */
    public byte[] generateDek() {
        byte[] dek = new byte[DEK_SIZE_BYTES];
        secureRandom.nextBytes(dek);
        return dek;
    }

    /**
     * Encrypts plaintext bytes using the provided DEK with AES-256-GCM.
     */
    public EncryptedPayload encrypt(byte[] plaintext, byte[] dek) {
        return encrypt(plaintext, dek, null);
    }

    /**
     * Encrypts plaintext bytes using the provided DEK with AES-256-GCM and optional AAD.
     */
    public EncryptedPayload encrypt(byte[] plaintext, byte[] dek, byte[] aad) {
        if (dek == null || dek.length == 0) {
            throw new IllegalArgumentException("DEK cannot be null or empty");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            SecretKey secretKey = new SecretKeySpec(dek, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            if (aad != null) {
                cipher.updateAAD(aad);
            }

            byte[] cipherText = cipher.doFinal(plaintext);
            String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherText);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            return new EncryptedPayload(cipherTextBase64, ivBase64);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        } finally {
            // Note: Caller retains responsibility for lifecycle zeroization of their DEK copy
            Arrays.fill(iv, (byte) 0);
        }
    }

    /**
     * Decrypts base64 ciphertext using the provided IV base64 and DEK with AES-256-GCM.
     */
    public byte[] decrypt(String ciphertextBase64, String ivBase64, byte[] dek) {
        return decrypt(ciphertextBase64, ivBase64, dek, null);
    }

    /**
     * Decrypts base64 ciphertext using the provided IV base64 and DEK with AES-256-GCM and optional AAD.
     */
    public byte[] decrypt(String ciphertextBase64, String ivBase64, byte[] dek, byte[] aad) {
        if (ciphertextBase64 == null || ivBase64 == null || dek == null) {
            throw new IllegalArgumentException("Ciphertext, IV, and DEK must not be null");
        }
        byte[] cipherText = null;
        byte[] iv = null;
        try {
            cipherText = Base64.getDecoder().decode(ciphertextBase64);
            iv = Base64.getDecoder().decode(ivBase64);

            SecretKey secretKey = new SecretKeySpec(dek, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            if (aad != null) {
                cipher.updateAAD(aad);
            }

            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        } finally {
            if (cipherText != null) {
                Arrays.fill(cipherText, (byte) 0);
            }
            if (iv != null) {
                Arrays.fill(iv, (byte) 0);
            }
        }
    }

    /**
     * Computes an HMAC-SHA256 blind index over normalized (lowercase, trimmed) input strings.
     * Produces a deterministic 64-character hexadecimal hash for O(1) encrypted searches without leaking PII.
     *
     * @param value the plaintext field value (e.g. NHS number, MRN, Last Name)
     * @param salt the blind indexing salt (if null/blank, falls back to configured salt)
     * @return 64-character hexadecimal HMAC-SHA256 blind index, or null if value is null/blank
     */
    public String computeBlindIndex(String value, String salt) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        String activeSalt = (salt != null && !salt.isBlank()) ? salt : configuredBlindIndexSalt;
        if (activeSalt == null || activeSalt.isBlank()) {
            activeSalt = "CRYPTOSHRED_BLIND_INDEX_DEFAULT_SALT";
        }

        byte[] saltBytes = activeSalt.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = null;
        try {
            String normalized = value.trim().toLowerCase();
            valueBytes = normalized.getBytes(StandardCharsets.UTF_8);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(saltBytes, "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(valueBytes);

            StringBuilder sb = new StringBuilder(64);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 blind index computation failed", e);
        } finally {
            Arrays.fill(saltBytes, (byte) 0);
            if (valueBytes != null) {
                Arrays.fill(valueBytes, (byte) 0);
            }
        }
    }

    /**
     * Securely zeroizes a sensitive byte array in memory.
     */
    public static void zeroize(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
