package com.blog.media.storage.cloudreve;

import java.time.Instant;
import java.util.Objects;

/** Short-lived server-side OAuth state and PKCE verifier. */
public final class CloudreveOAuthTransaction {
    private final String state;
    private final String codeVerifier;
    private final long adminId;
    private final Instant expiresAt;
    private final long authorizationGeneration;

    public CloudreveOAuthTransaction(String state, String codeVerifier, long adminId, Instant expiresAt) {
        this(state, codeVerifier, adminId, expiresAt, 0);
    }

    public CloudreveOAuthTransaction(String state, String codeVerifier, long adminId, Instant expiresAt,
                                     long authorizationGeneration) {
        this.state = requireText(state, "OAuth state is required");
        this.codeVerifier = requireText(codeVerifier, "PKCE verifier is required");
        if (adminId <= 0) throw new IllegalArgumentException("Administrator ID is required");
        this.adminId = adminId;
        this.expiresAt = Objects.requireNonNull(expiresAt, "OAuth expiry is required");
        if (authorizationGeneration < 0) throw new IllegalArgumentException("Authorization generation cannot be negative");
        this.authorizationGeneration = authorizationGeneration;
    }

    public String state() { return state; }
    public String codeVerifier() { return codeVerifier; }
    public long adminId() { return adminId; }
    public Instant expiresAt() { return expiresAt; }
    public long authorizationGeneration() { return authorizationGeneration; }

    @Override public String toString() {
        return "CloudreveOAuthTransaction[adminId=" + adminId + ", expiresAt=" + expiresAt + ", secrets=redacted]";
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }
}
