package com.blog.topic.dto;

import com.blog.topic.TopicStatus;

public record TopicResponse(Long id, String name, String slug, String description, String coverUrl,
                            TopicStatus status, int sortOrder) {
}
