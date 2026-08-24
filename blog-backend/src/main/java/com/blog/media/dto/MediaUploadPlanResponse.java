package com.blog.media.dto;

import com.blog.media.storage.UploadMode;

import java.time.Instant;
import java.util.Map;

/** Transfer instructions; the provider object key deliberately never crosses this boundary. */
public record MediaUploadPlanResponse(Long mediaId, UploadMode uploadMode, String method, String uploadUrl,
                                      Map<String, String> headers, Instant expiresAt) {
    public MediaUploadPlanResponse {
        headers = Map.copyOf(headers);
    }
}
