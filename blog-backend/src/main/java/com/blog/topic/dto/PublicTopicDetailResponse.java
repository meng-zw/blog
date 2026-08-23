package com.blog.topic.dto;

import com.blog.article.dto.ArticleSummaryResponse;

import java.util.List;

public record PublicTopicDetailResponse(Long id, String name, String slug, String description, String coverUrl,
                                        List<ArticleSummaryResponse> articles) {
}
