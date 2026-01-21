package com.blog.repository;

import com.blog.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工具数据访问接口
 */
@Repository
public interface ToolRepository extends JpaRepository<Tool, Long> {
    /**
     * 按创建时间倒序查询所有工具
     */
    @Query("SELECT t FROM Tool t ORDER BY t.createdAt DESC")
    List<Tool> findAllByOrderByCreatedAtDesc();

    /**
     * 获取工具总数
     */
    long count();
}