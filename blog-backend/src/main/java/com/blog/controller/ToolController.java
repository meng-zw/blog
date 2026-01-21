package com.blog.controller;

import com.blog.entity.Category;
import com.blog.entity.Tool;
import com.blog.entity.User;
import com.blog.repository.CategoryRepository;
import com.blog.repository.ToolRepository;
import com.blog.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 工具控制器，处理工具分享的CRUD操作
 */
@RestController
@RequestMapping("/tool")
@Transactional
public class ToolController {

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    /**
     * 创建工具
     * @param toolDTO 工具DTO
     * @return 创建结果
     */
    @PostMapping
    public ResponseEntity<?> createTool(@RequestBody ToolDTO toolDTO) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询用户
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        // 查询分类
        Category category = categoryRepository.findById(toolDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body("Category not found");
        }

        // 创建工具
        Tool tool = new Tool();
        tool.setName(toolDTO.getName());
        tool.setDescription(toolDTO.getDescription());
        tool.setUrl(toolDTO.getUrl());
        tool.setUser(user);
        tool.setCategory(category);
        tool.setViewCount(0L);
        tool.setCommentCount(0L);
        tool.setCreatedAt(new Date());
        tool.setUpdatedAt(new Date());

        // 保存工具
        toolRepository.save(tool);

        return ResponseEntity.status(HttpStatus.CREATED).body(tool);
    }

    /**
     * 获取工具列表
     * @param page 页码
     * @param size 每页大小
     * @return 工具列表
     */
    @GetMapping
    public ResponseEntity<?> getToolList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 计算偏移量
        int offset = page * size;

        // 查询工具列表
        List<Tool> tools = toolRepository.findAllByOrderByCreatedAtDesc();
        List<Tool> pagedTools = tools.stream()
                .skip(offset)
                .limit(size)
                .toList();

        // 查询工具总数
        Long total = (long) tools.size();

        // 构建响应
        ToolListResponse response = new ToolListResponse();
        response.setTools(pagedTools);
        response.setTotal(total);
        response.setPage(page);
        response.setSize(size);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取工具详情
     * @param id 工具ID
     * @return 工具详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getToolDetail(@PathVariable Long id) {
        // 查询工具
        Tool tool = toolRepository.findById(id).orElse(null);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        // 增加浏览量
        tool.setViewCount(tool.getViewCount() + 1);
        toolRepository.save(tool);

        return ResponseEntity.ok(tool);
    }

    /**
     * 更新工具
     * @param id 工具ID
     * @param toolDTO 工具DTO
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTool(@PathVariable Long id, @RequestBody ToolDTO toolDTO) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询工具
        Tool tool = toolRepository.findById(id).orElse(null);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查权限
        if (!tool.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You don't have permission to update this tool");
        }

        // 查询分类
        Category category = categoryRepository.findById(toolDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body("Category not found");
        }

        // 更新工具
        tool.setName(toolDTO.getName());
        tool.setDescription(toolDTO.getDescription());
        tool.setUrl(toolDTO.getUrl());
        tool.setCategory(category);
        tool.setUpdatedAt(new Date());

        // 保存更新
        toolRepository.save(tool);

        return ResponseEntity.ok(tool);
    }

    /**
     * 删除工具
     * @param id 工具ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTool(@PathVariable Long id) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询工具
        Tool tool = toolRepository.findById(id).orElse(null);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查权限
        if (!tool.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You don't have permission to delete this tool");
        }

        // 删除工具
        toolRepository.deleteById(id);

        return ResponseEntity.ok("Tool deleted successfully");
    }

    /**
     * 工具DTO
     */
    public static class ToolDTO {
        private String name;
        private String description;
        private String url;
        private Long categoryId;

        // getter和setter方法
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }
    }

    /**
     * 工具列表响应
     */
    public static class ToolListResponse {
        private List<Tool> tools;
        private Long total;
        private int page;
        private int size;

        // getter和setter方法
        public List<Tool> getTools() {
            return tools;
        }

        public void setTools(List<Tool> tools) {
            this.tools = tools;
        }

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }
}
