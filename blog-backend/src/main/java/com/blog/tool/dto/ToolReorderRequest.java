package com.blog.tool.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ToolReorderRequest(@NotNull List<@NotNull @Positive Long> toolIds) {
}
