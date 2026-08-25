package com.blog.media.storage.cloudreve;

/** Authoritative Cloudreve file record returned after upload or inspection. */
public record CloudreveFileMetadata(
        String path,
        String id,
        String contentType,
        long byteSize,
        String primaryEntity) {

    public CloudreveFileMetadata {
        path = requireText(path, "Cloudreve file path is required");
        id = requireText(id, "Cloudreve file ID is required");
        contentType = requireText(contentType, "Cloudreve file content type is required");
        primaryEntity = requireText(primaryEntity, "Cloudreve primary entity is required");
        if (byteSize < 0) throw new IllegalArgumentException("Cloudreve file size must not be negative");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
