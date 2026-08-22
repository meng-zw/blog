package com.blog.tool;

import com.blog.shared.web.PageResponse;
import com.blog.tool.dto.ToolDetailResponse;
import com.blog.tool.dto.ToolSummaryResponse;
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
@RequestMapping("/public/tools")
public class PublicToolController {
    private final ToolService toolService;

    public PublicToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    public PageResponse<ToolSummaryResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                   @RequestParam(required = false) @Size(max = 160) String category,
                                                   @RequestParam(required = false) @Size(max = 160) String tag,
                                                   @RequestParam(required = false) @Size(max = 100) String q) {
        return toolService.listPublic(page, size, category, tag, q);
    }

    @GetMapping("/{slug}")
    public ToolDetailResponse get(@PathVariable @Size(min = 1, max = 160) String slug) {
        return toolService.findPublishedBySlug(slug);
    }
}
