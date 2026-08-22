package com.blog.article;

import com.blog.article.dto.ArticleDetailResponse;
import com.blog.article.dto.ArticleSummaryResponse;
import com.blog.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/public/articles")
public class PublicArticleController {
    private final ArticleService articleService;

    public PublicArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    public PageResponse<ArticleSummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) ContentType contentType,
            @RequestParam(required = false) @Size(max = 160) String category,
            @RequestParam(required = false) @Size(max = 160) String tag,
            @RequestParam(required = false) @Size(max = 180) String topic,
            @RequestParam(required = false) @Size(max = 100) String q) {
        return articleService.listPublic(page, size, contentType, category, tag, topic, q);
    }

    @GetMapping("/{slug}")
    public ArticleDetailResponse get(@PathVariable @Size(min = 1, max = 160) String slug) {
        return articleService.findPublishedBySlug(slug);
    }
}
