package com.blog.media.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Instructions for one direct or proxied upload.
 */
public record UploadTicket(UploadMode mode, String method, URI uri, Map<String, String> requiredHeaders,
                           Instant expiresAt) {
    public UploadTicket {
        mode = Objects.requireNonNull(mode, "Upload mode is required");
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Upload method is required");
        }
        uri = Objects.requireNonNull(uri, "Upload URI is required");
        requiredHeaders = Map.copyOf(Objects.requireNonNull(requiredHeaders, "Required headers are required"));
        expiresAt = Objects.requireNonNull(expiresAt, "Upload expiry is required");
        method = method.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
