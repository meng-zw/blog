package com.blog.repository;

import com.blog.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户数据访问接口
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户实体
     */
    User findByUsername(String username);

    /**
     * 根据邮箱查询用户
     * @param email 邮箱
     * @return 用户实体
     */
    User findByEmail(String email);
}