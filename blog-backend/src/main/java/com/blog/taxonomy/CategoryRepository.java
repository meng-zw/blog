package com.blog.taxonomy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNormalizedName(String normalizedName);
    boolean existsBySlug(String slug);
    List<Category> findAllByScopeOrderBySortOrderAscNameAsc(CategoryScope scope);
    List<Category> findAllByOrderByScopeAscSortOrderAscNameAsc();
    Page<Category> findAllByScopeOrderBySortOrderAscNameAsc(CategoryScope scope, Pageable pageable);
    Page<Category> findAllByOrderByScopeAscSortOrderAscNameAsc(Pageable pageable);

    @Query(value = "select count(*) from article where category_id = :categoryId", nativeQuery = true)
    long countArticleReferences(long categoryId);

    @Query(value = "select count(*) from tool where category_id = :categoryId", nativeQuery = true)
    long countToolReferences(long categoryId);
}
