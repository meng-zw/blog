package com.blog.article;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    boolean existsBySlug(String slug);

    @Query("select distinct article from PublishingArticle article left join article.tags tag "
            + "left join article.category category left join article.topic topic "
            + "where article.status = com.blog.article.ArticleStatus.PUBLISHED and article.publishedAt <= :now "
            + "and (:contentType is null or article.contentType = :contentType) "
            + "and (:categorySlug is null or category.slug = :categorySlug) "
            + "and (:tagSlug is null or tag.slug = :tagSlug) "
            + "and (:topicSlug is null or (topic.slug = :topicSlug "
            + "and topic.status = com.blog.topic.TopicStatus.PUBLISHED)) "
            + "and (:keyword is null or lower(article.title) like lower(concat('%', :keyword, '%')) "
            + "or lower(article.summary) like lower(concat('%', :keyword, '%')))")
    Page<Article> findPublicPage(@Param("contentType") ContentType contentType,
                                 @Param("categorySlug") String categorySlug,
                                 @Param("tagSlug") String tagSlug,
                                 @Param("topicSlug") String topicSlug,
                                 @Param("keyword") String keyword,
                                 @Param("now") Instant now,
                                 Pageable pageable);

    @Query("select article from PublishingArticle article where (:status is null or article.status = :status) "
            + "and (:contentType is null or article.contentType = :contentType)")
    Page<Article> findAdminPage(@Param("status") ArticleStatus status,
                                @Param("contentType") ContentType contentType,
                                Pageable pageable);

    @EntityGraph(attributePaths = {"coverMedia", "category", "tags", "topic"})
    @Query("select article from PublishingArticle article where article.slug = :slug "
            + "and article.status = com.blog.article.ArticleStatus.PUBLISHED and article.publishedAt <= :now")
    Optional<Article> findPublishedBySlug(@Param("slug") String slug, @Param("now") Instant now);

    @Query("select article.id from PublishingArticle article "
            + "where article.status = com.blog.article.ArticleStatus.PUBLISHED and article.publishedAt <= :now "
            + "and article.contentType = com.blog.article.ContentType.ARTICLE "
            + "order by article.publishedAt desc, article.id desc")
    List<Long> findNewestVisibleArticleIds(@Param("now") Instant now, Pageable pageable);

    @Query("select article.id from PublishingArticle article "
            + "where article.status = com.blog.article.ArticleStatus.PUBLISHED and article.publishedAt <= :now "
            + "and article.contentType = com.blog.article.ContentType.ARTICLE "
            + "order by article.publishedAt desc, article.id desc")
    List<Long> findLatestVisibleArticleIds(@Param("now") Instant now, Pageable pageable);

    @EntityGraph(attributePaths = {"coverMedia", "category", "tags"})
    @Query("select distinct article from PublishingArticle article where article.id in :ids "
            + "and article.status = com.blog.article.ArticleStatus.PUBLISHED and article.publishedAt <= :now")
    List<Article> findVisibleSummariesByIdIn(@Param("ids") List<Long> ids, @Param("now") Instant now);

    @Query("select article.id as id, article.slug as slug from PublishingArticle article "
            + "where article.id > :afterId and article.status = com.blog.article.ArticleStatus.PUBLISHED "
            + "and article.publishedAt <= :now order by article.id asc")
    List<SitemapRow> findVisibleSitemapBatch(@Param("afterId") long afterId, @Param("now") Instant now,
                                             Pageable pageable);

    interface SitemapRow {
        Long getId();
        String getSlug();
    }

    @Query("select article from PublishingArticle article where article.status = com.blog.article.ArticleStatus.PUBLISHED "
            + "and article.publishedAt <= :now and article.contentType = :contentType "
            + "and (article.publishedAt < :publishedAt or (article.publishedAt = :publishedAt and article.id < :id)) "
            + "order by article.publishedAt desc, article.id desc")
    List<Article> findPreviousVisible(@Param("contentType") ContentType contentType,
                                      @Param("publishedAt") Instant publishedAt,
                                      @Param("id") long id, @Param("now") Instant now, Pageable pageable);

    @Query("select article from PublishingArticle article where article.status = com.blog.article.ArticleStatus.PUBLISHED "
            + "and article.publishedAt <= :now and article.contentType = :contentType "
            + "and (article.publishedAt > :publishedAt or (article.publishedAt = :publishedAt and article.id > :id)) "
            + "order by article.publishedAt asc, article.id asc")
    List<Article> findNextVisible(@Param("contentType") ContentType contentType,
                                  @Param("publishedAt") Instant publishedAt,
                                  @Param("id") long id, @Param("now") Instant now, Pageable pageable);

    @EntityGraph(attributePaths = {"coverMedia", "category", "tags", "topic"})
    @Query("select article from PublishingArticle article join TopicArticle placement on placement.articleId = article.id "
            + "where placement.topicId = :topicId and article.status = com.blog.article.ArticleStatus.PUBLISHED "
            + "and article.publishedAt <= :now order by placement.sortOrder asc, article.id asc")
    List<Article> findVisibleForTopic(@Param("topicId") long topicId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @EntityGraph(attributePaths = {"topic"})
    @Query("select article from PublishingArticle article left join article.topic topic "
            + "where article.status = com.blog.article.ArticleStatus.SCHEDULED "
            + "and article.scheduledAt <= :now and (article.topic is null "
            + "or topic.status = com.blog.topic.TopicStatus.PUBLISHED) "
            + "order by article.scheduledAt asc, article.id asc")
    List<Article> findDueForPublishing(@Param("now") Instant now, Pageable pageable);
}
