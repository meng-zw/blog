package com.blog.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.util.Date;
import java.util.List;

/**
 * 文章实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "article")
@EntityListeners(AuditingEntityListener.class)
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 标题
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 内容（markdown格式）
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 渲染后的HTML内容
     */
    @Column(name = "html_content", columnDefinition = "LONGTEXT")
    private String htmlContent;

    /**
     * 作者ID
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 分类ID
     */
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * 标签列表
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "article_tag",
        joinColumns = @JoinColumn(name = "article_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags;

    /**
     * 浏览量
     */
    @Column(name = "view_count", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long viewCount;

    /**
     * 评论数
     */
    @Column(name = "comment_count", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long commentCount;

    /**
     * 点赞数
     */
    @Column(name = "like_count", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long likeCount;

    /**
     * 文章状态：draft(草稿)/published(已发布)/scheduled(定时发布)
     */
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'published'")
    private String status = "published";

    /**
     * 发布时间（定时发布时记录实际发布时间；为空时按创建时间排序/归档）
     */
    @Column(name = "publish_time")
    private Date publishTime;

    /**
     * 封面图URL
     */
    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /**
     * 是否置顶
     */
    @Column(name = "is_top", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isTop = false;

    /**
     * 创建时间
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    /**
     * 更新时间
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

}
