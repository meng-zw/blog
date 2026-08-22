package com.blog.search.dto;

import java.time.Instant;

public record SearchResultResponse(SearchResultType type, Long id, String slug, String title, String summary,
                                   Instant publishedAt) {
}
