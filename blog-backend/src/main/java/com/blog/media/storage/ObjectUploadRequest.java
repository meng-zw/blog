package com.blog.media.storage;

/**
 * Server-generated object metadata. Clients never choose its object key.
 */
public record ObjectUploadRequest(String objectKey, String contentType, long byteSize) {
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
        objectKey = objectKey.trim();
        contentType = contentType.trim();
    }
}
