package com.blog.article.dto;

import com.blog.article.ArticleStatus;
import com.blog.article.ContentType;
import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.taxonomy.dto.TagResponse;

import java.time.Instant;
import java.util.List;

public record AdminArticleSummaryResponse(Long id, String slug, String title, String summary, ContentType contentType,
                                          ArticleStatus status, Instant publishedAt, Instant scheduledAt,
                                          String coverUrl, CategoryResponse category, List<TagResponse> tags) {
}
