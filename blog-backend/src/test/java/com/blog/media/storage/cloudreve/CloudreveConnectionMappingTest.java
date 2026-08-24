package com.blog.media.storage.cloudreve;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Types;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CloudreveConnectionMappingTest {

    @Test
    void mapsEncryptedTokensAndConnectionMetadataWithoutPersistingConfigurationSecrets() throws Exception {
        assertThat(CloudreveConnection.class.getDeclaredFields())
                .extracting(Field::getName)
                .contains("singletonKey", "authorizedSubject", "authorizedDisplayName", "accessTokenCiphertext",
                        "accessTokenNonce", "accessTokenExpiresAt", "refreshTokenCiphertext", "refreshTokenNonce",
                        "refreshTokenExpiresAt", "grantedScopes", "status", "version");

        assertThat(field("accessTokenCiphertext").getType()).isEqualTo(byte[].class);
        assertThat(field("accessTokenNonce").getType()).isEqualTo(byte[].class);
        assertThat(field("refreshTokenCiphertext").getType()).isEqualTo(byte[].class);
        assertThat(field("refreshTokenNonce").getType()).isEqualTo(byte[].class);
        assertThat(field("accessTokenCiphertext").getAnnotation(JdbcTypeCode.class).value())
                .isEqualTo(Types.LONGVARBINARY);
        assertThat(field("refreshTokenCiphertext").getAnnotation(JdbcTypeCode.class).value())
                .isEqualTo(Types.LONGVARBINARY);
        assertThat(field("accessTokenNonce").getAnnotation(JdbcTypeCode.class).value()).isEqualTo(Types.BINARY);
        assertThat(field("refreshTokenNonce").getAnnotation(JdbcTypeCode.class).value()).isEqualTo(Types.BINARY);
        assertThat(field("accessTokenExpiresAt").getType()).isEqualTo(Instant.class);
        assertThat(field("refreshTokenExpiresAt").getType()).isEqualTo(Instant.class);
        assertThat(field("status").getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertThat(field("version").getAnnotation(Version.class)).isNotNull();
        assertThat(CloudreveConnection.class.getDeclaredFields()).extracting(Field::getName)
                .doesNotContain("clientSecret", "tokenEncryptionKey", "accessToken", "refreshToken");
    }

    @Test
    void enforcesOneLogicalConnectionRecord() throws Exception {
        Column singletonKey = field("singletonKey").getAnnotation(Column.class);

        assertThat(singletonKey.nullable()).isFalse();
        assertThat(singletonKey.unique()).isTrue();
        assertThat(CloudreveConnectionRepository.class.getDeclaredMethod("findSingleton").getReturnType().getName())
                .isEqualTo("java.util.Optional");
    }

    private static Field field(String name) throws NoSuchFieldException {
        return CloudreveConnection.class.getDeclaredField(name);
    }
}
