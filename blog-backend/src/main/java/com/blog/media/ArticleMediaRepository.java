package com.blog.media;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleMediaRepository extends JpaRepository<ArticleMedia, ArticleMediaId> {
    @EntityGraph(attributePaths = "media")
    List<ArticleMedia> findByArticle_IdAndId_RoleOrderBySortOrderAsc(Long articleId, ArticleMediaRole role);

    boolean existsById_MediaId(Long mediaId);

    void deleteByArticle_Id(Long articleId);
}
