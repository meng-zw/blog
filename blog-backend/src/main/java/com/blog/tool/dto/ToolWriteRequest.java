package com.blog.tool.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record ToolWriteRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 160) @Pattern(regexp = "(?:|[a-z0-9]+(?:-[a-z0-9]+)*)") String slug,
        @NotBlank @Size(max = 500) String summary,
        @NotBlank @Size(max = 100000) String descriptionMarkdown,
        @NotBlank @Size(max = 1000) @OfficialHttpsUrl String officialUrl,
        @Positive Long coverMediaId,
        @Positive Long categoryId,
        @NotNull @Size(max = 50) Set<@NotNull @Positive Long> tagIds,
        @NotNull Boolean featured,
        @NotNull Integer sortOrder) {
}
