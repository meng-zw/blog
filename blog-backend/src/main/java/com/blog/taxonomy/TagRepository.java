package com.blog.taxonomy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByNormalizedName(String normalizedName);
    boolean existsBySlug(String slug);
    List<Tag> findAllByOrderByNameAsc();
    Page<Tag> findAllByOrderByNameAsc(Pageable pageable);
    @Query("select tag from TaxonomyTag tag where :keyword is null or lower(tag.name) like lower(concat('%', :keyword, '%')) order by tag.name asc")
    Page<Tag> findAdminPage(String keyword, Pageable pageable);

    @Query(value = "select count(*) from article_tag where tag_id = :tagId", nativeQuery = true)
    long countArticleReferences(long tagId);

    @Query(value = "select count(*) from tool_tag where tag_id = :tagId", nativeQuery = true)
    long countToolReferences(long tagId);
}
