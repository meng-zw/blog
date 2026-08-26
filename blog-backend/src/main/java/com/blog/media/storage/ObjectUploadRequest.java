package com.blog.media.storage;

/**
 * Server-generated object metadata. Clients never choose its object key.
 */
public record ObjectUploadRequest(String objectKey, String contentType, long byteSize, long maxBytes) {
    public ObjectUploadRequest(String objectKey, String contentType, long byteSize) {
        this(objectKey, contentType, byteSize, byteSize);
    }

    public ObjectUploadRequest {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }
        if (byteSize < 0) {
            throw new IllegalArgumentException("Byte size must not be negative");
        }
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Upload maximum must not be negative");
        }
        if (byteSize > maxBytes) {
            throw new IllegalArgumentException("Declared byte size exceeds the upload maximum");
        }
        objectKey = objectKey.trim();
        contentType = contentType.trim();
    }
}
