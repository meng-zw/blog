package com.blog.media.storage.cloudreve;

import com.blog.shared.persistence.AuditedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;

/** Durable encrypted credentials for the one administrator-owned Cloudreve connection. */
@Entity
@Table(name = "cloudreve_connection")
public class CloudreveConnection extends AuditedEntity {
    public static final byte SINGLETON_KEY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "singleton_key", nullable = false, unique = true, updatable = false)
    private byte singletonKey = SINGLETON_KEY;

    @Column(name = "authorized_subject", length = 255)
    private String authorizedSubject;

    @Column(name = "authorized_display_name", length = 255)
    private String authorizedDisplayName;

    @JsonIgnore
    @JdbcTypeCode(Types.LONGVARBINARY)
    @Column(name = "access_token_ciphertext", columnDefinition = "MEDIUMBLOB")
    private byte[] accessTokenCiphertext;

    @JsonIgnore
    @JdbcTypeCode(Types.BINARY)
    @Column(name = "access_token_nonce", length = 12)
    private byte[] accessTokenNonce;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @JsonIgnore
    @JdbcTypeCode(Types.LONGVARBINARY)
    @Column(name = "refresh_token_ciphertext", columnDefinition = "MEDIUMBLOB")
    private byte[] refreshTokenCiphertext;

    @JsonIgnore
    @JdbcTypeCode(Types.BINARY)
    @Column(name = "refresh_token_nonce", length = 12)
    private byte[] refreshTokenNonce;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "granted_scopes", length = 1000)
    private String grantedScopes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CloudreveConnectionStatus status = CloudreveConnectionStatus.DISCONNECTED;

    @Version
    @Column(nullable = false)
    private long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public byte getSingletonKey() { return singletonKey; }
    public String getAuthorizedSubject() { return authorizedSubject; }
    public void setAuthorizedSubject(String authorizedSubject) { this.authorizedSubject = authorizedSubject; }
    public String getAuthorizedDisplayName() { return authorizedDisplayName; }
    public void setAuthorizedDisplayName(String authorizedDisplayName) { this.authorizedDisplayName = authorizedDisplayName; }
    public byte[] getAccessTokenCiphertext() { return copy(accessTokenCiphertext); }
    public void setAccessTokenCiphertext(byte[] accessTokenCiphertext) { this.accessTokenCiphertext = copy(accessTokenCiphertext); }
    public byte[] getAccessTokenNonce() { return copy(accessTokenNonce); }
    public void setAccessTokenNonce(byte[] accessTokenNonce) { this.accessTokenNonce = copy(accessTokenNonce); }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) { this.accessTokenExpiresAt = accessTokenExpiresAt; }
    public byte[] getRefreshTokenCiphertext() { return copy(refreshTokenCiphertext); }
    public void setRefreshTokenCiphertext(byte[] refreshTokenCiphertext) { this.refreshTokenCiphertext = copy(refreshTokenCiphertext); }
    public byte[] getRefreshTokenNonce() { return copy(refreshTokenNonce); }
    public void setRefreshTokenNonce(byte[] refreshTokenNonce) { this.refreshTokenNonce = copy(refreshTokenNonce); }
    public Instant getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public void setRefreshTokenExpiresAt(Instant refreshTokenExpiresAt) { this.refreshTokenExpiresAt = refreshTokenExpiresAt; }
    public String getGrantedScopes() { return grantedScopes; }
    public void setGrantedScopes(String grantedScopes) { this.grantedScopes = grantedScopes; }
    public CloudreveConnectionStatus getStatus() { return status; }
    public void setStatus(CloudreveConnectionStatus status) { this.status = status; }
    public long getVersion() { return version; }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
