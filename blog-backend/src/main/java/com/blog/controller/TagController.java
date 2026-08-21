package com.blog.controller;

import com.blog.entity.Tag;
import com.blog.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 标签控制器，处理标签的CRUD操作（管理员专用）
 */
@RestController
@RequestMapping("/tags")
@Transactional
public class TagController {

    @Autowired
    private TagRepository tagRepository;

    /**
     * 获取所有标签列表
     */
    @GetMapping
    public ResponseEntity<?> getAllTags() {
        List<Tag> tags = tagRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(tags);
    }

    /**
     * 创建标签（仅管理员）
     */
    @PostMapping
    public ResponseEntity<?> createTag(@RequestBody CreateTagRequest request) {
        // 检查权限
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可操作"));
        }

        // 检查标签名称是否已存在
        if (tagRepository.existsByName(request.getName())) {
            return ResponseEntity.badRequest().body(errorBody("标签名称已存在"));
        }

        Tag tag = new Tag();
        tag.setName(request.getName());
        tag.setCreatedAt(new Date());

        tagRepository.save(tag);
        return ResponseEntity.status(HttpStatus.CREATED).body(tag);
    }

    /**
     * 更新标签（仅管理员）
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTag(@PathVariable Long id, @RequestBody CreateTagRequest request) {
        // 检查权限
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可操作"));
        }

        Tag tag = tagRepository.findById(id).orElse(null);
        if (tag == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查名称是否与其他标签重复
        Tag existingTag = tagRepository.findByName(request.getName());
        if (existingTag != null && existingTag.getId() != id) {
            return ResponseEntity.badRequest().body(errorBody("标签名称已存在"));
        }

        tag.setName(request.getName());
        tagRepository.save(tag);
        return ResponseEntity.ok(tag);
    }

    /**
     * 删除标签（仅管理员）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTag(@PathVariable Long id) {
        // 检查权限
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可操作"));
        }

        Tag tag = tagRepository.findById(id).orElse(null);
        if (tag == null) {
            return ResponseEntity.notFound().build();
        }

        tagRepository.deleteById(id);
        return ResponseEntity.ok(errorBody("标签删除成功"));
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private java.util.Map<String, String> errorBody(String message) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("message", message);
        return body;
    }

    /**
     * 创建/更新标签请求DTO
     */
    public static class CreateTagRequest {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
