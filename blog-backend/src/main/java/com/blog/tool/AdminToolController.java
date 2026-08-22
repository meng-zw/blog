package com.blog.tool;

import com.blog.shared.web.PageResponse;
import com.blog.tool.dto.AdminToolResponse;
import com.blog.tool.dto.AdminToolSummaryResponse;
import com.blog.tool.dto.ToolReorderRequest;
import com.blog.tool.dto.ToolWriteRequest;
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
@RequestMapping("/admin/tools")
public class AdminToolController {
    private final ToolService toolService;

    public AdminToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    public PageResponse<AdminToolSummaryResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                        @RequestParam(required = false) ToolStatus status) {
        return toolService.listAdmin(page, size, status);
    }

    @GetMapping("/{id}")
    public AdminToolResponse get(@PathVariable long id) {
        return toolService.findAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminToolResponse create(@Valid @RequestBody ToolWriteRequest request) {
        return toolService.createDraft(request);
    }

    @PutMapping("/{id}")
    public AdminToolResponse update(@PathVariable long id, @Valid @RequestBody ToolWriteRequest request) {
        return toolService.update(id, request);
    }

    @PostMapping("/{id}/publish")
    public AdminToolResponse publish(@PathVariable long id) {
        return toolService.publish(id);
    }

    @PostMapping("/{id}/archive")
    public AdminToolResponse archive(@PathVariable long id) {
        return toolService.archive(id);
    }

    @PostMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody ToolReorderRequest request) {
        toolService.reorder(request.toolIds());
    }
}
