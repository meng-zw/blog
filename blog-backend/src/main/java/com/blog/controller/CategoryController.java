package com.blog.controller;

import com.blog.entity.Category;
import com.blog.repository.CategoryRepository;
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
 * 分类控制器，处理分类的CRUD操作（管理员专用）
 */
@RestController
@RequestMapping("/categories")
@Transactional
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    /**
     * 获取所有分类列表
     */
    @GetMapping
    public ResponseEntity<?> getAllCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(categories);
    }

    /**
     * 获取文章分类列表
     */
    @GetMapping("/article")
    public ResponseEntity<?> getArticleCategories() {
        List<Category> categories = categoryRepository.findByType("article");
        return ResponseEntity.ok(categories);
    }

    /**
     * 获取工具分类列表
     */
    @GetMapping("/tool")
    public ResponseEntity<?> getToolCategories() {
        List<Category> categories = categoryRepository.findByType("tool");
        return ResponseEntity.ok(categories);
    }

    /**
     * 获取工具分类列表（兼容前端 /tool-categories 调用路径）
     */
    @GetMapping("/tool-categories")
    public ResponseEntity<?> getToolCategoriesAlias() {
        return getToolCategories();
    }

    /**
     * 创建分类（仅管理员）
     */
    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody CreateCategoryRequest request) {
        // 检查权限
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可操作"));
        }

        // 检查分类名称是否已存在
        if (categoryRepository.existsByName(request.getName())) {
            return ResponseEntity.badRequest().body(errorBody("分类名称已存在"));
        }

        Category category = new Category();
        category.setName(request.getName());
        category.setType(request.getType());
        category.setCreatedAt(new Date());
        category.setUpdatedAt(new Date());

        categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    /**
     * 更新分类（仅管理员）
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody CreateCategoryRequest request) {
        // 检查权限
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可操作"));
        }

        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查名称是否与其他分类重复
        Category existingCategory = categoryRepository.findByName(request.getName());
        if (existingCategory != null && existingCategory.getId() != id) {
            return ResponseEntity.badRequest().body(errorBody("分类名称已存在"));
        }

        category.setName(request.getName());
        category.setType(request.getType());
        category.setUpdatedAt(new Date());

        categoryRepository.save(category);
        return ResponseEntity.ok(category);
    }

    /**
     * 删除分类（仅管理员）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        // 检查权限
        if (!isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可操作"));
        }

        Category category = categoryRepository.findById(id).orElse(null);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }

        categoryRepository.deleteById(id);
        return ResponseEntity.ok(errorBody("分类删除成功"));
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
     * 创建/更新分类请求DTO
     */
    public static class CreateCategoryRequest {
        private String name;
        private String type; // "article" or "tool"

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
