package com.blog.taxonomy.dto;

import com.blog.taxonomy.CategoryScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 65535) String description,
        @NotNull Integer sortOrder,
        @NotNull CategoryScope scope) {
}
