package com.blog.repository;

import com.blog.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * 文章数据访问接口
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    /**
     * 公开文章过滤条件：仅已发布（兼容历史数据 status 为空的情况）
     */
    String PUBLISHED = "(a.status = 'published' OR a.status IS NULL)";

    /**
     * 查询所有已发布文章，置顶优先、按发布时间倒序
     */
    @Query("SELECT a FROM Article a WHERE " + PUBLISHED + " ORDER BY a.isTop DESC, COALESCE(a.publishTime, a.createdAt) DESC")
    List<Article> findPublishedAllByOrderByPublishTimeDesc();

    /**
     * 获取文章总数
     */
    long count();

    /**
     * 根据关键词搜索已发布文章（标题或内容）
     * @param keyword 搜索关键词
     * @return 匹配的文章列表
     */
    @Query("SELECT a FROM Article a WHERE " + PUBLISHED + " AND (a.title LIKE %:keyword% OR a.content LIKE %:keyword%) " +
            "ORDER BY a.isTop DESC, COALESCE(a.publishTime, a.createdAt) DESC")
    List<Article> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 根据分类ID查询已发布文章
     * @param categoryId 分类ID
     * @return 匹配的文章列表
     */
    @Query("SELECT a FROM Article a WHERE " + PUBLISHED + " AND a.category.id = :categoryId " +
            "ORDER BY a.isTop DESC, COALESCE(a.publishTime, a.createdAt) DESC")
    List<Article> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 分页查询用户的文章（含草稿等全部状态）
     * @param userId 用户ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<Article> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 根据标签ID查询已发布文章
     * @param tagId 标签ID
     * @return 匹配的文章列表
     */
    @Query("SELECT a FROM Article a JOIN a.tags t WHERE t.id = :tagId AND " + PUBLISHED + " " +
            "ORDER BY a.isTop DESC, COALESCE(a.publishTime, a.createdAt) DESC")
    List<Article> findByTagId(@Param("tagId") Long tagId);

    /**
     * 查询到期待发布的文章（定时任务用）
     */
    List<Article> findByStatusAndPublishTimeLessThanEqual(String status, Date time);

    /**
     * 查询上一篇已发布文章（发布时间早于当前文章的最接近一篇）
     */
    @Query("SELECT a FROM Article a WHERE " + PUBLISHED + " AND a.id <> :id " +
            "AND COALESCE(a.publishTime, a.createdAt) < (SELECT COALESCE(b.publishTime, b.createdAt) FROM Article b WHERE b.id = :id) " +
            "ORDER BY COALESCE(a.publishTime, a.createdAt) DESC")
    List<Article> findPrevPublishedArticle(@Param("id") Long id, Pageable pageable);

    /**
     * 查询下一篇已发布文章（发布时间晚于当前文章的最接近一篇）
     */
    @Query("SELECT a FROM Article a WHERE " + PUBLISHED + " AND a.id <> :id " +
            "AND COALESCE(a.publishTime, a.createdAt) > (SELECT COALESCE(b.publishTime, b.createdAt) FROM Article b WHERE b.id = :id) " +
            "ORDER BY COALESCE(a.publishTime, a.createdAt) ASC")
    List<Article> findNextPublishedArticle(@Param("id") Long id, Pageable pageable);

    /**
     * 查询相关文章（与当前文章共享标签的已发布文章，排除自身）
     */
    @Query("SELECT DISTINCT a FROM Article a JOIN a.tags t WHERE a.id <> :id AND " + PUBLISHED + " " +
            "AND t.id IN (SELECT t2.id FROM Article b JOIN b.tags t2 WHERE b.id = :id) " +
            "ORDER BY COALESCE(a.publishTime, a.createdAt) DESC")
    List<Article> findRelatedByTags(@Param("id") Long id, Pageable pageable);

    /**
     * 查询相关文章兜底（与当前文章同分类的已发布文章，排除自身）
     */
    @Query("SELECT a FROM Article a WHERE a.id <> :id AND " + PUBLISHED + " " +
            "AND a.category.id = (SELECT b.category.id FROM Article b WHERE b.id = :id) " +
            "ORDER BY COALESCE(a.publishTime, a.createdAt) DESC")
    List<Article> findRelatedByCategory(@Param("id") Long id, Pageable pageable);
}
