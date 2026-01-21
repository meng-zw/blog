package com.blog.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.util.Date;

/**
 * 标签实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "tag")
@EntityListeners(AuditingEntityListener.class)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 标签名称
     */
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /**
     * 创建时间
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

}
