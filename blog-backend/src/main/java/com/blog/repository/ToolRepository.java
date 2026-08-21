package com.blog.repository;

import com.blog.entity.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * 按浏览量倒序查询所有工具
     */
    @Query("SELECT t FROM Tool t ORDER BY t.viewCount DESC")
    List<Tool> findAllByOrderByViewCountDesc();

    /**
     * 获取工具总数
     */
    long count();

    /**
     * 根据关键词搜索工具（名称或描述）
     * @param keyword 搜索关键词
     * @return 匹配的工具列表
     */
    @Query("SELECT t FROM Tool t WHERE t.name LIKE %:keyword% OR t.description LIKE %:keyword%")
    List<Tool> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 根据分类ID查询工具
     * @param categoryId 分类ID
     * @return 匹配的工具列表
     */
    List<Tool> findByCategoryId(Long categoryId);

    /**
     * 分页查询用户的工具
     * @param userId 用户ID
     * @param page 分页参数
     * @return 分页结果
     */
    Page<Tool> findByUserId(@Param("userId") Long userId, Pageable page);

    /**
     * 根据标签ID查询工具
     * @param tagId 标签ID
     * @return 匹配的工具列表
     */
    @Query("SELECT t FROM Tool t JOIN t.tags tag WHERE tag.id = :tagId")
    List<Tool> findByTagId(@Param("tagId") Long tagId);
}