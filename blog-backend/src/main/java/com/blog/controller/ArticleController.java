package com.blog.controller;

import com.blog.entity.Article;
import com.blog.entity.Category;
import com.blog.entity.Favorite;
import com.blog.entity.LikeRecord;
import com.blog.entity.Tag;
import com.blog.entity.User;
import com.blog.repository.ArticleRepository;
import com.blog.repository.CategoryRepository;
import com.blog.repository.FavoriteRepository;
import com.blog.repository.LikeRepository;
import com.blog.repository.TagRepository;
import com.blog.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Autowired
    private FavoriteRepository favoriteRepository;

    /**
     * 创建文章
     * @param articleDTO 文章DTO
     * @return 创建结果
     */
    @PostMapping
    public ResponseEntity<?> createArticle(@RequestBody ArticleDTO articleDTO) {
        // 获取当前用户
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
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
        article.setCoverImage(articleDTO.getCoverImage());
        // 仅管理员可置顶
        if (Boolean.TRUE.equals(articleDTO.getIsTop())) {
            if (!isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可置顶文章"));
            }
            article.setIsTop(true);
        } else {
            article.setIsTop(false);
        }

        // 处理文章状态与发布时间
        String status = articleDTO.getStatus() != null ? articleDTO.getStatus() : "published";
        applyArticleStatus(article, status, articleDTO.getPublishTime());

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
     * @param tagId 标签ID筛选
     * @param categoryId 分类ID筛选
     * @param status 状态筛选（all=当前用户全部文章；其他值=当前用户指定状态文章；缺省=公开的已发布文章）
     * @return 文章列表
     */
    @GetMapping
    public ResponseEntity<?> getArticleList(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "tag_id", required = false) Long tagId,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "status", required = false) String status) {
        List<Article> articles;

        // 需要查看自己的文章（含草稿）时，仅允许本人查询
        if (status != null) {
            User user = getCurrentUser();
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
            }
            List<Article> myArticles = articleRepository.findByUserId(user.getId(), Pageable.unpaged()).getContent();
            if ("all".equals(status)) {
                articles = myArticles;
            } else {
                articles = myArticles.stream()
                        .filter(a -> status.equals(a.getStatus()))
                        .toList();
            }
        } else if (tagId != null) {
            articles = articleRepository.findByTagId(tagId);
        } else if (categoryId != null) {
            articles = articleRepository.findByCategoryId(categoryId);
        } else {
            articles = articleRepository.findPublishedAllByOrderByPublishTimeDesc();
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
        // 查询已发布文章，置顶优先、按发布时间倒序
        List<Article> articles = articleRepository.findPublishedAllByOrderByPublishTimeDesc();
        // 只返回最新的5篇
        List<Article> latest = articles.stream().limit(5).toList();
        return ResponseEntity.ok(latest);
    }

    /**
     * 获取文章归档（按年月分组，仅已发布文章）
     * @return 归档列表，如 [{ "month": "2026-08", "articles": [...] }]
     */
    @GetMapping("/archive")
    public ResponseEntity<?> getArticleArchive() {
        List<Article> articles = articleRepository.findPublishedAllByOrderByPublishTimeDesc();
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();

        for (Article a : articles) {
            Date time = a.getPublishTime() != null ? a.getPublishTime() : a.getCreatedAt();
            Calendar cal = Calendar.getInstance();
            cal.setTime(time);
            String monthKey = cal.get(Calendar.YEAR) + "-" + String.format("%02d", cal.get(Calendar.MONTH) + 1);

            // 归档条目使用精简字段，减少传输量
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle());
            item.put("time", time);
            item.put("category", a.getCategory() != null ? a.getCategory().getName() : "");
            item.put("view_count", a.getViewCount());
            item.put("comment_count", a.getCommentCount());

            groups.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(item);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> monthGroup = new LinkedHashMap<>();
            monthGroup.put("month", entry.getKey());
            monthGroup.put("articles", entry.getValue());
            result.add(monthGroup);
        }
        return ResponseEntity.ok(result);
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

        // 草稿/定时发布文章仅作者可见
        boolean isPublished = article.getStatus() == null || "published".equals(article.getStatus());
        if (!isPublished && !isCurrentUserOwner(article)) {
            return ResponseEntity.notFound().build();
        }

        // 已发布文章增加浏览量
        if (isPublished) {
            article.setViewCount(article.getViewCount() + 1);
            articleRepository.save(article);
        }

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
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        // 查询文章
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查权限
        if (!article.getUser().getUsername().equals(user.getUsername())) {
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
        article.setCoverImage(articleDTO.getCoverImage());

        // 置顶：仅管理员可修改
        if (articleDTO.getIsTop() != null) {
            if (Boolean.TRUE.equals(articleDTO.getIsTop()) && !isAdmin()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("权限不足，仅管理员可置顶文章"));
            }
            article.setIsTop(articleDTO.getIsTop());
        }

        // 处理标签
        if (articleDTO.getTagIds() != null) {
            if (articleDTO.getTagIds().isEmpty()) {
                article.setTags(new ArrayList<>());
            } else {
                List<Tag> tags = tagRepository.findAllById(articleDTO.getTagIds());
                article.setTags(tags);
            }
        }

        // 处理文章状态与发布时间
        if (articleDTO.getStatus() != null) {
            applyArticleStatus(article, articleDTO.getStatus(), articleDTO.getPublishTime());
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
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        // 查询文章
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查权限
        if (!article.getUser().getUsername().equals(user.getUsername())) {
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
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 检查是否已点赞
        Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
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
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        // 查找点赞记录
        Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
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
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.ok(new LikedResponse(false));
        }

        Optional<LikeRecord> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "article");
        return ResponseEntity.ok(new LikedResponse(existingLike.isPresent()));
    }

    /**
     * 收藏文章
     * @param id 文章ID
     * @return 收藏结果
     */
    @PostMapping("/{id}/favorite")
    public ResponseEntity<?> favoriteArticle(@PathVariable("id") Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        if (articleRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<Favorite> existing = favoriteRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "article");
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(errorBody("您已经收藏过了"));
        }

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setTargetId(id);
        favorite.setTargetType("article");
        favorite.setCreatedAt(new Date());
        favoriteRepository.save(favorite);

        return ResponseEntity.ok(errorBody("收藏成功"));
    }

    /**
     * 取消收藏文章
     * @param id 文章ID
     * @return 结果
     */
    @DeleteMapping("/{id}/favorite")
    public ResponseEntity<?> unfavoriteArticle(@PathVariable("id") Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        Optional<Favorite> existing = favoriteRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "article");
        if (existing.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("您还没有收藏"));
        }

        favoriteRepository.delete(existing.get());
        return ResponseEntity.ok(errorBody("已取消收藏"));
    }

    /**
     * 检查当前用户是否已收藏文章
     * @param id 文章ID
     * @return 收藏状态
     */
    @GetMapping("/{id}/favorited")
    public ResponseEntity<?> checkArticleFavorite(@PathVariable("id") Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.ok(new LikedResponse(false));
        }

        Optional<Favorite> existing = favoriteRepository.findByUserIdAndTargetIdAndTargetType(
                user.getId(), id, "article");
        return ResponseEntity.ok(new LikedResponse(existing.isPresent()));
    }

    /**
     * 获取当前用户收藏的文章列表（仅已发布）
     * @return 收藏的文章精简列表
     */
    @GetMapping("/favorites")
    public ResponseEntity<?> getFavoriteArticles() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("用户未登录"));
        }

        List<Favorite> favorites = favoriteRepository.findByUserIdAndTargetTypeOrderByCreatedAtDesc(user.getId(), "article");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Favorite favorite : favorites) {
            Article article = articleRepository.findById(favorite.getTargetId()).orElse(null);
            // 仅返回已发布文章，草稿/定时中的收藏不展示
            if (article != null && ("published".equals(article.getStatus()) || article.getStatus() == null)) {
                result.add(toArticleBrief(article));
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 获取上一篇/下一篇已发布文章
     * @param id 文章ID
     * @return { prev: {...}|null, next: {...}|null }
     */
    @GetMapping("/{id}/neighbors")
    public ResponseEntity<?> getNeighbors(@PathVariable("id") Long id) {
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        Pageable one = PageRequest.of(0, 1);
        List<Article> prevList = articleRepository.findPrevPublishedArticle(id, one);
        List<Article> nextList = articleRepository.findNextPublishedArticle(id, one);

        Map<String, Object> result = new HashMap<>();
        result.put("prev", prevList.isEmpty() ? null : toArticleBrief(prevList.get(0)));
        result.put("next", nextList.isEmpty() ? null : toArticleBrief(nextList.get(0)));
        return ResponseEntity.ok(result);
    }

    /**
     * 获取相关文章（优先按共享标签匹配，不足时按同分类补充）
     * @param id 文章ID
     * @param limit 返回数量
     * @return 相关文章列表
     */
    @GetMapping("/{id}/related")
    public ResponseEntity<?> getRelatedArticles(@PathVariable("id") Long id,
                                                 @RequestParam(name = "limit", defaultValue = "4") int limit) {
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) {
            return ResponseEntity.notFound().build();
        }

        Pageable pageable = PageRequest.of(0, limit);
        List<Article> related = new ArrayList<>(articleRepository.findRelatedByTags(id, pageable));
        if (related.size() < limit) {
            // 按同分类补充不足部分
            List<Article> byCategory = articleRepository.findRelatedByCategory(id, pageable);
            for (Article a : byCategory) {
                if (related.size() >= limit) {
                    break;
                }
                if (related.stream().noneMatch(r -> r.getId().equals(a.getId()))) {
                    related.add(a);
                }
            }
        }

        List<Map<String, Object>> result = related.stream()
                .map(this::toArticleBrief)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 将文章转换为精简响应（列表/相关文章用）
     */
    private Map<String, Object> toArticleBrief(Article a) {
        Map<String, Object> brief = new HashMap<>();
        brief.put("id", a.getId());
        brief.put("title", a.getTitle());
        brief.put("time", a.getPublishTime() != null ? a.getPublishTime() : a.getCreatedAt());
        brief.put("category", a.getCategory() != null ? a.getCategory().getName() : "");
        brief.put("view_count", a.getViewCount());
        brief.put("cover_image", a.getCoverImage());
        return brief;
    }

    /**
     * 根据目标状态设置文章状态与发布时间
     */
    private void applyArticleStatus(Article article, String status, Date publishTime) {
        if (status == null || status.isBlank()) {
            status = "published";
        }
        switch (status) {
            case "draft" -> {
                article.setStatus("draft");
                article.setPublishTime(null);
            }
            case "scheduled" -> {
                if (publishTime == null) {
                    throw new IllegalArgumentException("定时发布必须指定发布时间");
                }
                article.setStatus("scheduled");
                article.setPublishTime(publishTime);
            }
            default -> {
                // 已发布：草稿转发布时设置发布时间，重复发布保留原发布时间
                article.setStatus("published");
                if (article.getPublishTime() == null) {
                    article.setPublishTime(new Date());
                }
            }
        }
    }

    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        return userRepository.findByUsername(userDetails.getUsername());
    }

    /**
     * 判断当前登录用户是否为文章作者
     */
    private boolean isCurrentUserOwner(Article article) {
        User user = getCurrentUser();
        return user != null && article.getUser().getUsername().equals(user.getUsername());
    }

    /**
     * 判断当前用户是否为管理员
     */
    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return false;
        }
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * 点赞/收藏状态响应DTO
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
        private List<Long> tagIds;
        private String status;
        @JsonProperty("publish_time")
        private Date publishTime;
        @JsonProperty("cover_image")
        private String coverImage;
        @JsonProperty("is_top")
        private Boolean isTop;

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

        public List<Long> getTagIds() {
            return tagIds;
        }

        public void setTagIds(List<Long> tagIds) {
            this.tagIds = tagIds;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Date getPublishTime() {
            return publishTime;
        }

        public void setPublishTime(Date publishTime) {
            this.publishTime = publishTime;
        }

        public String getCoverImage() {
            return coverImage;
        }

        public void setCoverImage(String coverImage) {
            this.coverImage = coverImage;
        }

        public Boolean getIsTop() {
            return isTop;
        }

        public void setIsTop(Boolean isTop) {
            this.isTop = isTop;
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
    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
