package com.blog.taxonomy.dto;

import com.blog.taxonomy.CategoryScope;

public record CategoryResponse(Long id, String name, String slug, String description, int sortOrder,
                               CategoryScope scope) {
}
