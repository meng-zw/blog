package com.blog.topic.dto;

import com.blog.topic.TopicStatus;
import java.util.List;

public record AdminTopicResponse(Long id, String name, String slug, String description, String coverUrl,
                                 Long coverMediaId, TopicStatus status, int sortOrder, List<Long> articleIds) {
}
