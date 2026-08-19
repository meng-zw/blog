package com.blog.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * 评论实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "comment")
@EntityListeners(AuditingEntityListener.class)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 评论内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 评论者ID
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 目标ID（文章ID或工具ID）
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /**
     * 目标类型（article/tool）
     */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /**
     * 父评论ID（用于回复，不序列化避免循环引用）
     */
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Comment parent;

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

    /**
     * 序列化时直接输出评论者用户名，便于前端展示
     * @return 评论者用户名
     */
    @JsonProperty("username")
    public String getUsername() {
        return user == null ? null : user.getUsername();
    }

}
