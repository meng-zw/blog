package com.blog.tool.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ToolReorderRequest(@NotNull @Size(max = 1000) List<@NotNull @Positive Long> toolIds) {
}
