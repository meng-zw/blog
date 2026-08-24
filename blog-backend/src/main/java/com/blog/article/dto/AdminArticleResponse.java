package com.blog.article.dto;

import com.blog.article.ArticleStatus;
import com.blog.article.ContentType;
import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.taxonomy.dto.TagResponse;
import com.blog.topic.dto.TopicResponse;

import java.time.Instant;
import java.util.List;

public record AdminArticleResponse(Long id, String slug, String title, String summary, String markdownContent,
                                   String renderedHtml, ContentType contentType, ArticleStatus status,
                                   Instant publishedAt, Instant scheduledAt, String coverUrl, Long coverMediaId,
                                   CategoryResponse category, List<TagResponse> tags, TopicResponse topic,
                                   String seoTitle, String seoDescription,
                                   List<ArticleAttachmentResponse> attachments) {
}
