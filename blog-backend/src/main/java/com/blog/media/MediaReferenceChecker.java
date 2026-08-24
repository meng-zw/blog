package com.blog.media;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

    /** Constant-query page lookup; never call the single-item check in a list mapper. */
    public Set<Long> referencedIds(Collection<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return Set.of();
        Set<Long> ids = new HashSet<>(mediaIds);
        Set<Long> referenced = new HashSet<>();
        addIds(referenced, "select article.coverMedia.id from PublishingArticle article where article.coverMedia.id in :ids", ids);
        addIds(referenced, "select topic.coverMedia.id from Topic topic where topic.coverMedia.id in :ids", ids);
        addIds(referenced, "select tool.coverMedia.id from PublishingTool tool where tool.coverMedia.id in :ids", ids);
        addIds(referenced, "select profile.avatarMedia.id from SiteProfile profile where profile.avatarMedia.id in :ids", ids);
        addIds(referenced, "select articleMedia.id.mediaId from ArticleMedia articleMedia where articleMedia.id.mediaId in :ids", ids);
        return Set.copyOf(referenced);
    }

    private long count(String query, long mediaId) {
        return entityManager.createQuery(query, Long.class).setParameter("mediaId", mediaId).getSingleResult();
    }

    private void addIds(Set<Long> target, String query, Set<Long> ids) {
        target.addAll(entityManager.createQuery(query, Long.class).setParameter("ids", ids).getResultList());
    }
}
