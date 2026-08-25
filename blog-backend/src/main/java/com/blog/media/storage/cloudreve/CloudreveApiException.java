package com.blog.media.storage.cloudreve;

/** Provider-neutral classification for Cloudreve file API and content transport failures. */
public final class CloudreveApiException extends RuntimeException {
    public enum Kind { NOT_FOUND, CONFLICT, TRANSIENT, PROVIDER_FAILURE }

    private final Kind kind;

    CloudreveApiException(Kind kind, String message) {
        this(kind, message, null);
    }

    CloudreveApiException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = java.util.Objects.requireNonNull(kind, "Cloudreve failure kind is required");
    }

    public Kind kind() {
        return kind;
    }
}
