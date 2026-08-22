package com.blog.topic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TopicArticleRepository extends JpaRepository<TopicArticle, TopicArticle.Id> {
    List<TopicArticle> findByTopicIdOrderBySortOrderAsc(long topicId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TopicArticle article where article.topicId = :topicId")
    void deleteByTopicId(@Param("topicId") long topicId);

    @Query(value = "select count(*) from article where id in (:articleIds)", nativeQuery = true)
    long countExistingArticlesByIds(@Param("articleIds") List<Long> articleIds);
}
