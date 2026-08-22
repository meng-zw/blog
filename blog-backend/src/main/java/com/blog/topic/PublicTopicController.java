package com.blog.topic;

import com.blog.topic.dto.TopicResponse;
import com.blog.topic.dto.TopicDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/topics")
public class PublicTopicController {
    private final TopicService topicService;

    public PublicTopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public List<TopicResponse> list() {
        return topicService.listPublished();
    }

    @GetMapping("/{slug}")
    public TopicDetailResponse get(@PathVariable String slug) {
        return topicService.findPublishedDetailBySlug(slug);
    }
}
