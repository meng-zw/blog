package com.blog.controller;

import com.blog.entity.Article;
import com.blog.entity.Category;
import com.blog.entity.User;
import com.blog.repository.ArticleRepository;
import com.blog.repository.CategoryRepository;
import com.blog.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 文章控制器，处理文章的CRUD操作
 */
@RestController
@RequestMapping("/articles")
@Transactional
public class ArticleController {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    /**
     * 创建文章
     * @param articleDTO 文章DTO
     * @return 创建结果
     */
    @PostMapping
    public ResponseEntity<?> createArticle(@RequestBody ArticleDTO articleDTO) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询用户
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        // 查询分类
        Category category = categoryRepository.findById(articleDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body("Category not found");
        }

        // 创建文章
        Article article = new Article();
        article.setTitle(articleDTO.getTitle());
        article.setContent(articleDTO.getContent());
        article.setHtmlContent(articleDTO.getHtmlContent());
        article.setUser(user);
        article.setCategory(category);
        article.setViewCount(0L);
        article.setCommentCount(0L);
        article.setCreatedAt(new Date());
        article.setUpdatedAt(new Date());

        // 保存文章
        articleRepository.save(article);

        return ResponseEntity.status(HttpStatus.CREATED).body(article);
    }

    /**
     * 获取文章列表
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    @GetMapping
    public ResponseEntity<?> getArticleList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // 计算偏移量
        int offset = page * size;

        // 查询文章列表
        List<Article> articles = articleRepository.findAllByOrderByCreatedAtDesc();
        List<Article> pagedArticles = articles.stream()
                .skip(offset)
                .limit(size)
                .toList();

        // 查询文章总数
        Long total = (long) articles.size();

        // 构建响应
        ArticleListResponse response = new ArticleListResponse();
        response.setArticles(pagedArticles);
        response.setTotal(total);
        response.setPage(page);
        response.setSize(size);

        return ResponseEntity.ok(response);
    }

    /**
     * 获取文章详情
     * @param id 文章ID
     * @return 文章详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getArticleDetail(@PathVariable Long id) {
        // 查询文章
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 增加浏览量
        article.setViewCount(article.getViewCount() + 1);
        articleRepository.save(article);

        return ResponseEntity.ok(article);
    }

    /**
     * 更新文章
     * @param id 文章ID
     * @param articleDTO 文章DTO
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateArticle(@PathVariable Long id, @RequestBody ArticleDTO articleDTO) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询文章
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查权限
        if (!article.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You don't have permission to update this article");
        }

        // 查询分类
        Category category = categoryRepository.findById(articleDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body("Category not found");
        }

        // 更新文章
        article.setTitle(articleDTO.getTitle());
        article.setContent(articleDTO.getContent());
        article.setHtmlContent(articleDTO.getHtmlContent());
        article.setCategory(category);
        article.setUpdatedAt(new Date());

        // 保存更新
        articleRepository.save(article);

        return ResponseEntity.ok(article);
    }

    /**
     * 删除文章
     * @param id 文章ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteArticle(@PathVariable Long id) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 查询文章
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查权限
        if (!article.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You don't have permission to delete this article");
        }

        // 删除文章
        articleRepository.deleteById(id);

        return ResponseEntity.ok("Article deleted successfully");
    }

    /**
     * 文章DTO
     */
    public static class ArticleDTO implements Serializable {
        private String title;
        private String content;
        private String htmlContent;
        @JsonProperty("category_id")
        private Long categoryId;

        // getter和setter方法
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getHtmlContent() {
            return htmlContent;
        }

        public void setHtmlContent(String htmlContent) {
            this.htmlContent = htmlContent;
        }

        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }
    }

    /**
     * 文章列表响应
     */
    public static class ArticleListResponse {
        private List<Article> articles;
        private Long total;
        private int page;
        private int size;

        // getter和setter方法
        public List<Article> getArticles() {
            return articles;
        }

        public void setArticles(List<Article> articles) {
            this.articles = articles;
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
