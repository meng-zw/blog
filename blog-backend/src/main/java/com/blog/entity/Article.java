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
