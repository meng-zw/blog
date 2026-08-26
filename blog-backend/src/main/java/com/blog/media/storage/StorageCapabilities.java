package com.blog.media.storage;

/**
 * Storage features that determine how clients transfer and read media.
 */
public record StorageCapabilities(boolean directUpload, boolean publicRead, PublicAccessMode publicAccessMode) {
    public StorageCapabilities(boolean directUpload, boolean publicRead) {
        this(directUpload, publicRead, PublicAccessMode.PROXY);
    }

    public StorageCapabilities {
        if (publicAccessMode == null) throw new IllegalArgumentException("Public access mode is required");
    }

    public enum PublicAccessMode {
        REDIRECT,
        PROXY
    }
}
