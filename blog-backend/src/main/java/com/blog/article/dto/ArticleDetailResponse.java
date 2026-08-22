package com.blog.article.dto;

import com.blog.article.ContentType;
import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.taxonomy.dto.TagResponse;
import com.blog.topic.dto.TopicResponse;

import java.time.Instant;
import java.util.List;

public record ArticleDetailResponse(Long id, String slug, String title, String summary, ContentType contentType,
                                    Instant publishedAt, String coverUrl, CategoryResponse category,
                                    List<TagResponse> tags, TopicResponse topic, String renderedHtml,
                                    String seoTitle, String seoDescription,
                                    ArticleSummaryResponse previous, ArticleSummaryResponse next) {
}
