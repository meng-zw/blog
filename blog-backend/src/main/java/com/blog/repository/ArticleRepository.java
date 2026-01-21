package com.blog.repository;

import com.blog.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}