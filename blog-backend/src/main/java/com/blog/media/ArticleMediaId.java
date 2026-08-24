package com.blog.media;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ArticleMediaId implements Serializable {
    private Long articleId;
    private Long mediaId;

    @Enumerated(EnumType.STRING)
    private ArticleMediaRole role;

    protected ArticleMediaId() {
    }

    public ArticleMediaId(Long articleId, Long mediaId, ArticleMediaRole role) {
        this.articleId = articleId;
        this.mediaId = mediaId;
        this.role = role;
    }

    public Long getArticleId() { return articleId; }
    public Long getMediaId() { return mediaId; }
    public ArticleMediaRole getRole() { return role; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ArticleMediaId that)) return false;
        return Objects.equals(articleId, that.articleId)
                && Objects.equals(mediaId, that.mediaId)
                && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId, mediaId, role);
    }
}
