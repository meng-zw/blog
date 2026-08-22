package com.blog.article;

import com.blog.article.dto.ArticleWriteRequest;
import com.blog.article.dto.ScheduleArticleRequest;
import com.blog.article.dto.AdminArticleResponse;
import com.blog.article.dto.AdminArticleSummaryResponse;
import com.blog.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/articles")
public class AdminArticleController {
    private final ArticleService articleService;

    public AdminArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public PageResponse<AdminArticleSummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) ContentType contentType) {
        return articleService.listAdmin(page, size, status, contentType);
    }

    @GetMapping("/{id}")
    public AdminArticleResponse get(@PathVariable long id) {
        return articleService.findAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminArticleResponse create(@Valid @RequestBody ArticleWriteRequest request) {
        return articleService.createDraft(request);
    }

    @PutMapping("/{id}")
    public AdminArticleResponse update(@PathVariable long id, @Valid @RequestBody ArticleWriteRequest request) {
        return articleService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    public AdminArticleResponse publish(@PathVariable long id) {
        return articleService.publishNow(id);
    }

    @PostMapping("/{id}/schedule")
    public AdminArticleResponse schedule(@PathVariable long id, @Valid @RequestBody ScheduleArticleRequest request) {
        return articleService.schedule(id, request.scheduledAt());
    }

    @PostMapping("/{id}/archive")
    public AdminArticleResponse archive(@PathVariable long id) {
        return articleService.archive(id);
    }
}
