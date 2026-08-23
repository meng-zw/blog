package com.blog.media.storage;

/**
 * Authoritative object metadata returned after an upload or inspection.
 */
public record StoredObject(String key, String contentType, long byteSize, String etag) {
    public StoredObject {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Object key is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }
        if (byteSize < 0) {
            throw new IllegalArgumentException("Byte size must not be negative");
        }
        key = key.trim();
        contentType = contentType.trim();
    }
}
