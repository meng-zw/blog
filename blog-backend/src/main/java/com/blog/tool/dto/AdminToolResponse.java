package com.blog.tool.dto;

import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.taxonomy.dto.TagResponse;
import com.blog.tool.ToolStatus;

import java.time.Instant;
import java.util.List;

public record AdminToolResponse(Long id, String slug, String name, String summary, String descriptionMarkdown,
                                String renderedHtml, String officialUrl, String coverUrl, CategoryResponse category,
                                List<TagResponse> tags, ToolStatus status, boolean featured, int sortOrder,
                                Instant publishedAt, Instant createdAt, Instant updatedAt) {
}
