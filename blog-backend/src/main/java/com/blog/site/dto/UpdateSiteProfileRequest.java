package com.blog.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSiteProfileRequest(
        @NotBlank @Size(max = 160) String siteTitle,
        @NotBlank @Size(max = 160) String subtitle,
        @NotBlank @Size(max = 120) String nickname,
        @NotBlank @Size(max = 10_000) String bio,
        Long avatarMediaId,
        @NotBlank @Size(max = 500) @Pattern(regexp = "https://.+", message = "must use HTTPS") String githubUrl) {
}
