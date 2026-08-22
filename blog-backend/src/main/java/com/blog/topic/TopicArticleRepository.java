package com.blog.topic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicArticleRepository extends JpaRepository<TopicArticle, TopicArticle.Id> {
    List<TopicArticle> findByTopicIdOrderBySortOrderAsc(long topicId);
    Optional<TopicArticle> findByArticleId(long articleId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TopicArticle article where article.topicId = :topicId")
    void deleteByTopicId(@Param("topicId") long topicId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TopicArticle article where article.articleId = :articleId")
    void deleteByArticleId(@Param("articleId") long articleId);

}
