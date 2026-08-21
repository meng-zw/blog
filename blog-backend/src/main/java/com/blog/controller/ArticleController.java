package com.blog.controller;

import com.blog.entity.Article;
import com.blog.entity.Category;
import com.blog.entity.LikeRecord;
import com.blog.entity.Tag;
import com.blog.entity.User;
import com.blog.repository.ArticleRepository;
import com.blog.repository.CategoryRepository;
import com.blog.repository.LikeRepository;
import com.blog.repository.TagRepository;
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

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private LikeRepository likeRepository;

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
            return ResponseEntity.badRequest().body(errorBody("用户不存在"));
        }

        // 查询分类
        Category category = categoryRepository.findById(articleDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body(errorBody("文章分类不存在"));
        }

        // 创建文章
        Article article = new Article();
        article.setTitle(articleDTO.getTitle());
        article.setContent(articleDTO.getContent());
        article.setHtmlContent(articleDTO.getHtmlContent());
        article.setUser(user);
        article.setCategory(category);

        // 处理标签
        if (articleDTO.getTagIds() != null && !articleDTO.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(articleDTO.getTagIds());
            if (tags.size() != articleDTO.getTagIds().size()) {
                return ResponseEntity.badRequest().body(errorBody("部分标签不存在，请重新选择"));
            }
            article.setTags(tags);
        }

        article.setViewCount(0L);
        article.setCommentCount(0L);
        article.setLikeCount(0L);
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
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "tag_id", required = false) Long tagId,
            @RequestParam(name = "category_id", required = false) Long categoryId) {
        List<Article> articles;
        if (tagId != null) {
            articles = articleRepository.findByTagId(tagId);
        } else if (categoryId != null) {
            articles = articleRepository.findByCategoryId(categoryId);
        } else {
            articles = articleRepository.findAllByOrderByCreatedAtDesc();
        }
        // 计算偏移量
        int offset = page * size;

        // 分页处理
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
     * 获取最新文章列表（首页用）
     * @return 最新5篇文章
     */
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestArticles() {
        // 查询文章列表，按创建时间倒序
        List<Article> articles = articleRepository.findAllByOrderByCreatedAtDesc();
        // 只返回最新的5篇
        List<Article> latest = articles.stream().limit(5).toList();
        return ResponseEntity.ok(latest);
    }

    /**
     * 获取文章详情
     * @param id 文章ID
     * @return 文章详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getArticleDetail(@PathVariable("id") Long id) {
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
    public ResponseEntity<?> updateArticle(@PathVariable("id") Long id, @RequestBody ArticleDTO articleDTO) {
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("没有权限编辑他人的文章"));
        }

        // 查询分类
        Category category = categoryRepository.findById(articleDTO.getCategoryId()).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body(errorBody("文章分类不存在"));
        }

        // 更新文章
        article.setTitle(articleDTO.getTitle());
        article.setContent(articleDTO.getContent());
        article.setHtmlContent(articleDTO.getHtmlContent());
        article.setCategory(category);

        // 处理标签
        if (articleDTO.getTagIds() != null) {
            if (articleDTO.getTagIds().isEmpty()) {
                article.setTags(new java.util.ArrayList<>());
            } else {
                List<Tag> tags = tagRepository.findAllById(articleDTO.getTagIds());
                article.setTags(tags);
            }
        }
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
    public ResponseEntity<?> deleteArticle(@PathVariable("id") Long id) {
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("没有权限删除他人的文章"));
        }

        // 删除文章
        articleRepository.deleteById(id);

        return ResponseEntity.ok(errorBody("文章删除成功"));
    }

    /**
     * 点赞文章
     * @param id 文章ID
     * @return 点赞结果
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<?> likeArticle(@PathVariable("id") Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查是否已点赞
        java.util.Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "article");
        if (existingLike.isPresent()) {
            return ResponseEntity.badRequest().body(errorBody("您已经点过赞了"));
        }

        // 创建点赞记录
        LikeRecord like = new LikeRecord();
        like.setUser(user);
        like.setTargetId(id);
        like.setTargetType("article");
        like.setCreatedAt(new Date());
        likeRepository.save(like);

        // 更新文章点赞数
        long likeCount = likeRepository.countByTargetIdAndTargetType(id, "article");
        article.setLikeCount(likeCount);
        articleRepository.save(article);

        return ResponseEntity.ok(errorBody("点赞成功"));
    }

    /**
     * 取消点赞文章
     * @param id 文章ID
     * @return 结果
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<?> unlikeArticle(@PathVariable("id") Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 查找点赞记录
        java.util.Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "article");
        if (existingLike.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("您还没有点赞"));
        }

        // 删除点赞记录
        likeRepository.delete(existingLike.get());

        // 更新文章点赞数
        long likeCount = likeRepository.countByTargetIdAndTargetType(id, "article");
        article.setLikeCount(likeCount);
        articleRepository.save(article);

        return ResponseEntity.ok(errorBody("已取消点赞"));
    }

    /**
     * 检查当前用户是否已点赞文章
     * @param id 文章ID
     * @return 点赞状态
     */
    @GetMapping("/{id}/liked")
    public ResponseEntity<?> checkArticleLike(@PathVariable("id") Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username);

        if (user == null) {
            return ResponseEntity.ok(new LikedResponse(false));
        }

        java.util.Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "article");
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
     * 文章DTO
     */
    public static class ArticleDTO implements Serializable {
        private String title;
        private String content;
        private String htmlContent;
        @JsonProperty("category_id")
        private Long categoryId;
        @JsonProperty("tag_ids")
        private java.util.List<Long> tagIds;

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

        public java.util.List<Long> getTagIds() {
            return tagIds;
        }

        public void setTagIds(java.util.List<Long> tagIds) {
            this.tagIds = tagIds;
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

    /**
     * 构造统一的错误响应体
     */
    private java.util.Map<String, String> errorBody(String message) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("message", message);
        return body;
    }
}
