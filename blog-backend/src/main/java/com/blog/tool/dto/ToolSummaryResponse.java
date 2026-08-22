package com.blog.tool.dto;

import com.blog.article.dto.PublicCategoryResponse;
import com.blog.article.dto.PublicTagResponse;

import java.time.Instant;
import java.util.List;

public record ToolSummaryResponse(Long id, String slug, String name, String summary, String officialUrl,
                                  String coverUrl, PublicCategoryResponse category, List<PublicTagResponse> tags,
                                  boolean featured, Instant publishedAt) {
}
