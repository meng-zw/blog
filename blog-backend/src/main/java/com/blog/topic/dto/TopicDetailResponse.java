package com.blog.topic.dto;

import com.blog.article.dto.ArticleSummaryResponse;
import com.blog.topic.TopicStatus;

import java.util.List;

public record TopicDetailResponse(Long id, String name, String slug, String description, String coverUrl,
                                  TopicStatus status, int sortOrder, List<ArticleSummaryResponse> articles) {
}
