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
 * 用户实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "user")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名
     */
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * 密码（加密存储）
     */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    /**
     * 邮箱
     */
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    /**
     * 手机号
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 头像
     */
    @Column(name = "avatar", length = 200)
    private String avatar;

    /**
     * 角色（user/admin）
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

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
