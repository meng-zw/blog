package com.blog.tool;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    boolean existsBySlug(String slug);

    @Query("select distinct tool from PublishingTool tool left join tool.tags tag left join tool.category category "
            + "where tool.status = com.blog.tool.ToolStatus.PUBLISHED and tool.publishedAt <= :now "
            + "and (:categorySlug is null or category.slug = :categorySlug) "
            + "and (:tagSlug is null or tag.slug = :tagSlug) "
            + "and (:keyword is null or lower(tool.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(tool.summary) like lower(concat('%', :keyword, '%')))" )
    Page<Tool> findPublicPage(@Param("categorySlug") String categorySlug, @Param("tagSlug") String tagSlug,
                              @Param("keyword") String keyword, @Param("now") Instant now, Pageable pageable);

    @EntityGraph(attributePaths = {"coverMedia", "category", "tags"})
    @Query("select tool from PublishingTool tool where tool.slug = :slug "
            + "and tool.status = com.blog.tool.ToolStatus.PUBLISHED and tool.publishedAt <= :now")
    Optional<Tool> findPublishedBySlug(@Param("slug") String slug, @Param("now") Instant now);

    @Query("select tool from PublishingTool tool where (:status is null or tool.status = :status)")
    Page<Tool> findAdminPage(@Param("status") ToolStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tool from PublishingTool tool where tool.status = com.blog.tool.ToolStatus.PUBLISHED "
            + "order by tool.sortOrder asc, tool.id asc")
    List<Tool> findAllPublishedForReorder();
}
