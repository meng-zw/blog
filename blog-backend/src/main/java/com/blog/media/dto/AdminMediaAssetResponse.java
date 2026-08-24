package com.blog.media.dto;

import com.blog.media.MediaPurpose;
import com.blog.media.MediaStatus;
import com.blog.media.StorageProvider;

import java.time.Instant;

/** Administrative media-library projection. Object keys and provider URLs are never exposed. */
public record AdminMediaAssetResponse(Long mediaId, String filename, String contentType, long byteSize,
                                      Integer width, Integer height, StorageProvider provider, MediaStatus status,
                                      MediaPurpose purpose, boolean referenced, String url, Instant createdAt) {
}
