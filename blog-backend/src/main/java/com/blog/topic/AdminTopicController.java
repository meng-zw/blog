package com.blog.topic;

import com.blog.topic.dto.TopicResponse;
import com.blog.topic.dto.TopicWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/admin/topics")
public class AdminTopicController {
    private final TopicService topicService;

    public AdminTopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public List<TopicResponse> list() {
        return topicService.listAdmin();
    }

    @GetMapping("/{id}")
    public TopicResponse get(@PathVariable long id) {
        return topicService.findAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TopicResponse create(@Valid @RequestBody TopicWriteRequest request) {
        return topicService.create(request);
    }

    @PutMapping("/{id}")
    public TopicResponse update(@PathVariable long id, @Valid @RequestBody TopicWriteRequest request) {
        return topicService.update(id, request);
    }

    @PutMapping("/{id}/articles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@PathVariable long id, @RequestBody List<@NotNull @Positive Long> articleIds) {
        topicService.reorderArticles(id, articleIds);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        topicService.delete(id);
    }
}
