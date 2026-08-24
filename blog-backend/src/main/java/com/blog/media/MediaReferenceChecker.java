package com.blog.media;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Centralizes legacy foreign-key reference checks. Article inline and attachment references join this check in Task 5.
 */
@Component
public class MediaReferenceChecker {
    private final EntityManager entityManager;

    public MediaReferenceChecker(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean isReferenced(long mediaId) {
        return count("select count(article) from PublishingArticle article where article.coverMedia.id = :mediaId", mediaId) > 0
                || count("select count(topic) from Topic topic where topic.coverMedia.id = :mediaId", mediaId) > 0
                || count("select count(tool) from PublishingTool tool where tool.coverMedia.id = :mediaId", mediaId) > 0
                || count("select count(profile) from SiteProfile profile where profile.avatarMedia.id = :mediaId", mediaId) > 0
                || count("select count(articleMedia) from ArticleMedia articleMedia where articleMedia.id.mediaId = :mediaId", mediaId) > 0;
    }

    private long count(String query, long mediaId) {
        return entityManager.createQuery(query, Long.class).setParameter("mediaId", mediaId).getSingleResult();
    }
}
