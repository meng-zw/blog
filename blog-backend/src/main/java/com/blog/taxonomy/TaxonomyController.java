package com.blog.taxonomy;

import com.blog.taxonomy.dto.CategoryRequest;
import com.blog.taxonomy.dto.CategoryResponse;
import com.blog.taxonomy.dto.TagRequest;
import com.blog.taxonomy.dto.TagResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.blog.shared.web.PageResponse;

@RestController
@RequestMapping
public class TaxonomyController {
    private final TaxonomyService taxonomyService;

    public TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/public/taxonomy/categories")
    public List<CategoryResponse> publicCategories(@RequestParam(required = false) CategoryScope scope) {
        return taxonomyService.listCategories(scope);
    }

    @GetMapping("/public/taxonomy/tags")
    public List<TagResponse> publicTags() {
        return taxonomyService.listTags();
    }

    @GetMapping("/admin/taxonomy/categories")
    public PageResponse<CategoryResponse> adminCategories(@RequestParam(required = false) CategoryScope scope,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return taxonomyService.pageCategories(scope, page, size);
    }

    @PostMapping("/admin/taxonomy/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return taxonomyService.createCategory(request);
    }

    @PutMapping("/admin/taxonomy/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable long id, @Valid @RequestBody CategoryRequest request) {
        return taxonomyService.updateCategory(id, request);
    }

    @DeleteMapping("/admin/taxonomy/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable long id) {
        taxonomyService.deleteCategory(id);
    }

    @GetMapping("/admin/taxonomy/tags")
    public PageResponse<TagResponse> adminTags(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return taxonomyService.pageTags(keyword, page, size);
    }

    @PostMapping("/admin/taxonomy/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse createTag(@Valid @RequestBody TagRequest request) {
        return taxonomyService.createTag(request);
    }

    @PutMapping("/admin/taxonomy/tags/{id}")
    public TagResponse updateTag(@PathVariable long id, @Valid @RequestBody TagRequest request) {
        return taxonomyService.updateTag(id, request);
    }

    @DeleteMapping("/admin/taxonomy/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTag(@PathVariable long id) {
        taxonomyService.deleteTag(id);
    }
}
