package com.blog.repository;

import com.blog.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文章数据访问接口
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    /**
     * 按创建时间倒序查询所有文章
     */
    @Query("SELECT a FROM Article a ORDER BY a.createdAt DESC")
    List<Article> findAllByOrderByCreatedAtDesc();

    /**
     * 获取文章总数
     */
    long count();

    /**
     * 根据关键词搜索文章（标题或内容）
     * @param keyword 搜索关键词
     * @return 匹配的文章列表
     */
    @Query("SELECT a FROM Article a WHERE a.title LIKE %:keyword% OR a.content LIKE %:keyword%")
    List<Article> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 根据分类ID查询文章
     * @param categoryId 分类ID
     * @return 匹配的文章列表
     */
    List<Article> findByCategoryId(Long categoryId);

    /**
     * 分页查询用户的文章
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Article> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 根据标签ID查询文章
     * @param tagId 标签ID
     * @return 匹配的文章列表
     */
    @Query("SELECT a FROM Article a JOIN a.tags t WHERE t.id = :tagId")
    List<Article> findByTagId(@Param("tagId") Long tagId);
}
