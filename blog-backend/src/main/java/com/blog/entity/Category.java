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
 * 分类实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "category")
@EntityListeners(AuditingEntityListener.class)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 分类名称
     */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 分类类型（article/tool）
     */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

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
