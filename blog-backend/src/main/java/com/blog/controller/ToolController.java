package com.blog.controller;

import com.blog.entity.Category;
import com.blog.entity.LikeRecord;
import com.blog.entity.Tag;
import com.blog.entity.Tool;
import com.blog.entity.User;
import com.blog.repository.CategoryRepository;
import com.blog.repository.LikeRepository;
import com.blog.repository.TagRepository;
import com.blog.repository.ToolRepository;
import com.blog.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@RequestMapping("/tools")
@Transactional
public class ToolController {

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private LikeRepository likeRepository;

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
            return ResponseEntity.badRequest().body(errorBody("用户不存在"));
        }

        // 查询分类
        Category category = categoryRepository.findById(toolDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body(errorBody("工具分类不存在"));
        }

        // 创建工具
        Tool tool = new Tool();
        tool.setName(toolDTO.getName());
        tool.setDescription(toolDTO.getDescription());
        tool.setUrl(toolDTO.getUrl());
        tool.setUser(user);
        tool.setCategory(category);

        // 处理标签
        if (toolDTO.getTagIds() != null && !toolDTO.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(toolDTO.getTagIds());
            if (tags.size() != toolDTO.getTagIds().size()) {
                return ResponseEntity.badRequest().body(errorBody("部分标签不存在，请重新选择"));
            }
            tool.setTags(tags);
        }

        tool.setViewCount(0L);
        tool.setCommentCount(0L);
        tool.setLikeCount(0L);
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
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "tag_id", required = false) Long tagId,
            @RequestParam(name = "category_id", required = false) Long categoryId) {
        List<Tool> tools;
        if (tagId != null) {
            tools = toolRepository.findByTagId(tagId);
        } else if (categoryId != null) {
            tools = toolRepository.findByCategoryId(categoryId);
        } else {
            tools = toolRepository.findAllByOrderByCreatedAtDesc();
        }
        // 计算偏移量
        int offset = page * size;

        // 分页处理
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
     * 获取热门工具列表（首页用）
     * @return 热门工具列表
     */
    @GetMapping("/popular")
    public ResponseEntity<?> getPopularTools() {
        // 查询工具列表，按浏览量倒序
        List<Tool> tools = toolRepository.findAllByOrderByViewCountDesc();
        // 只返回前5个
        List<Tool> popular = tools.stream().limit(5).toList();
        return ResponseEntity.ok(popular);
    }

    /**
     * 获取工具详情
     * @param id 工具ID
     * @return 工具详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getToolDetail(@PathVariable("id") Long id) {
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
    public ResponseEntity<?> updateTool(@PathVariable("id") Long id, @RequestBody ToolDTO toolDTO) {
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("没有权限编辑他人分享的工具"));
        }

        // 查询分类
        Category category = categoryRepository.findById(toolDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body(errorBody("工具分类不存在"));
        }

        // 更新工具
        tool.setName(toolDTO.getName());
        tool.setDescription(toolDTO.getDescription());
        tool.setUrl(toolDTO.getUrl());
        tool.setCategory(category);

        // 处理标签
        if (toolDTO.getTagIds() != null) {
            if (toolDTO.getTagIds().isEmpty()) {
                tool.setTags(new java.util.ArrayList<>());
            } else {
                List<Tag> tags = tagRepository.findAllById(toolDTO.getTagIds());
                tool.setTags(tags);
            }
        }
        tool.setUpdatedAt(new Date());

        // 保存更新
        toolRepository.save(tool);

        return ResponseEntity.ok(tool);
    }

    /**
     * 点赞工具
     * @param id 工具ID
     * @return 点赞结果
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<?> likeTool(@PathVariable("id") Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        Tool tool = toolRepository.findById(id).orElse(null);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查是否已点赞
        java.util.Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "tool");
        if (existingLike.isPresent()) {
            return ResponseEntity.badRequest().body(errorBody("您已经点过赞了"));
        }

        // 创建点赞记录
        LikeRecord like = new LikeRecord();
        like.setUser(user);
        like.setTargetId(id);
        like.setTargetType("tool");
        like.setCreatedAt(new Date());
        likeRepository.save(like);

        // 更新工具点赞数
        long likeCount = likeRepository.countByTargetIdAndTargetType(id, "tool");
        tool.setLikeCount(likeCount);
        toolRepository.save(tool);

        return ResponseEntity.ok(errorBody("点赞成功"));
    }

    /**
     * 取消点赞工具
     * @param id 工具ID
     * @return 结果
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<?> unlikeTool(@PathVariable("id") Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        Tool tool = toolRepository.findById(id).orElse(null);
        if (tool == null) {
            return ResponseEntity.notFound().build();
        }

        // 查找点赞记录
        java.util.Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "tool");
        if (existingLike.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("您还没有点赞"));
        }

        // 删除点赞记录
        likeRepository.delete(existingLike.get());

        // 更新工具点赞数
        long likeCount = likeRepository.countByTargetIdAndTargetType(id, "tool");
        tool.setLikeCount(likeCount);
        toolRepository.save(tool);

        return ResponseEntity.ok(errorBody("已取消点赞"));
    }

    /**
     * 检查当前用户是否已点赞工具
     * @param id 工具ID
     * @return 点赞状态
     */
    @GetMapping("/{id}/liked")
    public ResponseEntity<?> checkToolLike(@PathVariable("id") Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);

        if (user == null) {
            return ResponseEntity.ok(new LikedResponse(false));
        }

        java.util.Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "tool");
        return ResponseEntity.ok(new LikedResponse(existingLike.isPresent()));
    }

    /**
     * 点赞响应DTO
     */
    public static class LikedResponse {
        private boolean liked;

        public LikedResponse(boolean liked) {
            this.liked = liked;
        }

        public boolean isLiked() {
            return liked;
        }
    }

    /**
     * 删除工具
     * @param id 工具ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTool(@PathVariable("id") Long id) {
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("没有权限删除他人分享的工具"));
        }

        // 删除工具
        toolRepository.deleteById(id);

        return ResponseEntity.ok("Tool deleted successfully");
    }

    /**
     * 构造统一的错误响应体，保证前端能从 message 字段读到具体原因
     */
    private java.util.Map<String, String> errorBody(String message) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("message", message);
        return body;
    }

    /**
     * 工具DTO
     */
    public static class ToolDTO {
        private String name;
        private String description;
        private String url;
        @JsonProperty("category_id")
        private Long categoryId;
        @JsonProperty("tag_ids")
        private java.util.List<Long> tagIds;

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

        public java.util.List<Long> getTagIds() {
            return tagIds;
        }

        public void setTagIds(java.util.List<Long> tagIds) {
            this.tagIds = tagIds;
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
