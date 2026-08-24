package com.blog.media.storage;

import com.blog.media.StorageProvider;

/** Immutable persisted object address. All provider operations must obey every component. */
public record ObjectLocation(StorageProvider provider, String bucket, String objectKey) {
    public ObjectLocation {
        if (provider == null) throw new IllegalArgumentException("Storage provider is required");
        bucket = bucket == null ? "" : bucket.strip();
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("Object key is required");
        objectKey = objectKey.strip();
    }
}
