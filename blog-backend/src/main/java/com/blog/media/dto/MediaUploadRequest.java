package com.blog.media.dto;

import com.blog.media.MediaPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Client declaration used to obtain one server-owned upload plan. */
public record MediaUploadRequest(
        @NotBlank @Size(max = 500) String filename,
        @NotBlank @Size(max = 120) String contentType,
        @Positive long byteSize,
        @NotNull MediaPurpose purpose) {
}
