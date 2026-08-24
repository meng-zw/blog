package com.blog.media.storage.cloudreve;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Encrypts persisted Cloudreve credentials with record-bound AES-256-GCM. */
public final class CloudreveTokenCipher {
    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public CloudreveTokenCipher(String base64Key) {
        Objects.requireNonNull(base64Key, "Cloudreve token encryption key is required");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cloudreve token encryption key must be valid base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Cloudreve token encryption key must contain exactly 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
        Arrays.fill(decoded, (byte) 0);
    }

    public EncryptedToken encrypt(long connectionId, String tokenType, String plaintext) {
        requireContext(connectionId, tokenType);
        Objects.requireNonNull(plaintext, "Token is required");
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] clear = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(connectionId, tokenType));
            byte[] sealed = cipher.doFinal(clear);
            byte[] versioned = ByteBuffer.allocate(1 + sealed.length).put(FORMAT_VERSION).put(sealed).array();
            return new EncryptedToken(nonce, versioned);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cloudreve token encryption failed", exception);
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
    }

    public String decrypt(long connectionId, String tokenType, EncryptedToken encrypted) {
        requireContext(connectionId, tokenType);
        Objects.requireNonNull(encrypted, "Encrypted token is required");
        byte[] nonce = encrypted.nonce();
        byte[] versioned = encrypted.ciphertext();
        if (nonce.length != NONCE_BYTES || versioned.length < 2 || versioned[0] != FORMAT_VERSION) {
            throw new TokenDecryptionException();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(connectionId, tokenType));
            byte[] clear = cipher.doFinal(versioned, 1, versioned.length - 1);
            try {
                return new String(clear, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(clear, (byte) 0);
            }
        } catch (AEADBadTagException exception) {
            throw new TokenDecryptionException();
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new TokenDecryptionException();
        }
    }

    private static void requireContext(long connectionId, String tokenType) {
        if (connectionId <= 0 || tokenType == null || tokenType.isBlank()) {
            throw new IllegalArgumentException("Token encryption context is required");
        }
    }

    private static byte[] aad(long connectionId, String tokenType) {
        return ("cloudreve-token:v1:" + connectionId + ":" + tokenType).getBytes(StandardCharsets.UTF_8);
    }

    public record EncryptedToken(byte[] nonce, byte[] ciphertext) {
        public EncryptedToken {
            nonce = Arrays.copyOf(Objects.requireNonNull(nonce), nonce.length);
            ciphertext = Arrays.copyOf(Objects.requireNonNull(ciphertext), ciphertext.length);
        }

        @Override public byte[] nonce() { return Arrays.copyOf(nonce, nonce.length); }
        @Override public byte[] ciphertext() { return Arrays.copyOf(ciphertext, ciphertext.length); }
        @Override public String toString() { return "EncryptedToken[redacted]"; }
    }

    public static final class TokenDecryptionException extends RuntimeException {
        TokenDecryptionException() { super("Cloudreve token could not be decrypted"); }
    }
}
