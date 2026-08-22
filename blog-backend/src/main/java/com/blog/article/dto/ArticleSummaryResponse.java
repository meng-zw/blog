package com.blog.article.dto;

import com.blog.article.ContentType;
import java.time.Instant;
import java.util.List;

public record ArticleSummaryResponse(Long id, String slug, String title, String summary, ContentType contentType,
                                     Instant publishedAt, String coverUrl, PublicCategoryResponse category,
                                     List<PublicTagResponse> tags) {
}
