package com.blog.media.storage.cloudreve;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudreveTokenCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void usesANewNonceForEveryEncryption() {
        CloudreveTokenCipher cipher = new CloudreveTokenCipher(KEY);

        CloudreveTokenCipher.EncryptedToken first = cipher.encrypt(7L, "access", "same-token");
        CloudreveTokenCipher.EncryptedToken second = cipher.encrypt(7L, "access", "same-token");

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void requiresTheSameRecordAndTokenTypeAsAdditionalAuthenticatedData() {
        CloudreveTokenCipher cipher = new CloudreveTokenCipher(KEY);
        CloudreveTokenCipher.EncryptedToken encrypted = cipher.encrypt(7L, "access", "secret-token");

        assertThatThrownBy(() -> cipher.decrypt(8L, "access", encrypted))
                .isInstanceOf(CloudreveTokenCipher.TokenDecryptionException.class);
        assertThatThrownBy(() -> cipher.decrypt(7L, "refresh", encrypted))
                .isInstanceOf(CloudreveTokenCipher.TokenDecryptionException.class);
        assertThat(cipher.decrypt(7L, "access", encrypted)).isEqualTo("secret-token");
    }

    @Test
    void rejectsTamperedOrUnknownVersionCiphertextWithoutReturningPlaintext() {
        CloudreveTokenCipher cipher = new CloudreveTokenCipher(KEY);
        CloudreveTokenCipher.EncryptedToken encrypted = cipher.encrypt(7L, "access", "secret-token");
        byte[] tampered = encrypted.ciphertext();
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(7L, "access",
                new CloudreveTokenCipher.EncryptedToken(encrypted.nonce(), tampered)))
                .isInstanceOf(CloudreveTokenCipher.TokenDecryptionException.class)
                .hasMessageNotContaining("secret-token");

        byte[] unknownVersion = encrypted.ciphertext();
        unknownVersion[0] = 99;
        assertThatThrownBy(() -> cipher.decrypt(7L, "access",
                new CloudreveTokenCipher.EncryptedToken(encrypted.nonce(), unknownVersion)))
                .isInstanceOf(CloudreveTokenCipher.TokenDecryptionException.class);
    }

    @Test
    void encryptedValueDoesNotRevealSecretThroughToString() {
        CloudreveTokenCipher cipher = new CloudreveTokenCipher(KEY);
        String secret = "do-not-print-this-token";

        CloudreveTokenCipher.EncryptedToken encrypted = cipher.encrypt(7L, "access", secret);

        assertThat(encrypted.toString()).doesNotContain(secret)
                .doesNotContain(new String(encrypted.ciphertext(), StandardCharsets.UTF_8));
    }

    @Test
    void aDifferentEncryptionKeyCannotDecryptTheToken() {
        CloudreveTokenCipher first = new CloudreveTokenCipher(KEY);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        CloudreveTokenCipher second = new CloudreveTokenCipher(Base64.getEncoder().encodeToString(otherKey));
        CloudreveTokenCipher.EncryptedToken encrypted = first.encrypt(7L, "access", "secret-token");

        assertThatThrownBy(() -> second.decrypt(7L, "access", encrypted))
                .isInstanceOf(CloudreveTokenCipher.TokenDecryptionException.class);
    }
}
