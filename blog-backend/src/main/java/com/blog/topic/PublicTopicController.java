package com.blog.topic;

import com.blog.shared.web.PageResponse;
import com.blog.topic.dto.PublicTopicDetailResponse;
import com.blog.topic.dto.PublicTopicSummaryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/public/topics")
public class PublicTopicController {
    private final TopicService topicService;

    public PublicTopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public PageResponse<PublicTopicSummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return topicService.listPublished(page, size);
    }

    @GetMapping("/{slug}")
    public PublicTopicDetailResponse get(@PathVariable String slug) {
        return topicService.findPublishedDetailBySlug(slug);
    }
}
