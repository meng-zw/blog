package com.blog.taxonomy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByNormalizedName(String normalizedName);
    boolean existsBySlug(String slug);
    List<Tag> findAllByOrderByNameAsc();

    @Query(value = "select count(*) from article_tag where tag_id = :tagId", nativeQuery = true)
    long countArticleReferences(long tagId);

    @Query(value = "select count(*) from tool_tag where tag_id = :tagId", nativeQuery = true)
    long countToolReferences(long tagId);
}
