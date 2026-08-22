package com.blog.topic.dto;

import com.blog.topic.TopicStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record TopicWriteRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 65535) String description,
        @Positive Long coverMediaId,
        @NotNull TopicStatus status,
        @NotNull @Size(max = 10000) List<@NotNull @Positive Long> articleIds,
        @NotNull @PositiveOrZero Integer sortOrder) {
}
