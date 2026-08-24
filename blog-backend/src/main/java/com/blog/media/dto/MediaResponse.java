package com.blog.media.dto;

import com.blog.media.MediaPurpose;
import com.blog.media.MediaStatus;

/** Stable, provider-neutral metadata returned after an upload has become ready. */
public record MediaResponse(Long mediaId, String filename, String contentType, long byteSize, Integer width,
                            Integer height, MediaStatus status, MediaPurpose purpose, String url) {
}
