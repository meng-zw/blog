package com.blog.topic;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Optional<Topic> findByNormalizedName(String normalizedName);
    Optional<Topic> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Topic> findAllByOrderBySortOrderAscIdAsc();
    List<Topic> findAllByStatusOrderBySortOrderAscIdAsc(TopicStatus status);

    @EntityGraph(attributePaths = {"coverMedia"})
    @Query("select topic from Topic topic where topic.status = :status order by topic.sortOrder asc, topic.id asc")
    List<Topic> findPublishedForHome(@Param("status") TopicStatus status, Pageable pageable);

    @Query("select topic.id as id, topic.slug as slug from Topic topic where topic.id > :afterId "
            + "and topic.status = com.blog.topic.TopicStatus.PUBLISHED order by topic.id asc")
    List<SitemapRow> findPublishedSitemapBatch(@Param("afterId") long afterId, Pageable pageable);

    interface SitemapRow {
        Long getId();
        String getSlug();
    }
}
