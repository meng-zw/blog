package com.blog.article.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScheduleArticleRequest(@NotNull Instant scheduledAt) {
}
