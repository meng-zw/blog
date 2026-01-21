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
 * 工具实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "tool")
@EntityListeners(AuditingEntityListener.class)
public class Tool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 工具名称
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 工具描述
     */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * 工具链接
     */
    @Column(name = "url", nullable = false, length = 200)
    private String url;

    /**
     * 发布者ID
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
