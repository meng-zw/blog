package com.blog.search;

import com.blog.search.dto.SearchResultResponse;
import com.blog.shared.web.PageResponse;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/public/search")
public class PublicSearchController {
    private final SearchService searchService;

    public PublicSearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public PageResponse<SearchResultResponse> search(@RequestParam(required = false) String q,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return searchService.search(q, page, size);
    }
}
