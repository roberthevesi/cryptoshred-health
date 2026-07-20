package com.roberthevesi.cryptoshred_health.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EnvelopeEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes
    private static final int DEK_SIZE_BYTES = 32;  // 256 bits

    private final SecureRandom secureRandom = new SecureRandom();

    public record EncryptedPayload(String ciphertextBase64, String ivBase64) {}

    /** Generates a cryptographically strong random 256-bit DEK. */
    public byte[] generateDek() {
        byte[] dek = new byte[DEK_SIZE_BYTES];
        secureRandom.nextBytes(dek);
        return dek;
    }

    /** Encrypts plaintext bytes using the provided DEK with AES-256-GCM. */
    public EncryptedPayload encrypt(byte[] plaintext, byte[] dek) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            SecretKey secretKey = new SecretKeySpec(dek, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext);

            String cipherTextBase64 = Base64.getEncoder().encodeToString(cipherText);
            String ivBase64 = Base64.getEncoder().encodeToString(iv);

            return new EncryptedPayload(cipherTextBase64, ivBase64);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** Decrypts base64 ciphertext using the provided IV base64 and DEK with AES-256-GCM. */
    public byte[] decrypt(String ciphertextBase64, String ivBase64, byte[] dek) {
        try {
            byte[] cipherText = Base64.getDecoder().decode(ciphertextBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            SecretKey secretKey = new SecretKeySpec(dek, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }
}
