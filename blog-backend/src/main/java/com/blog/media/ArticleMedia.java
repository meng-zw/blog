package com.blog.media;

import com.blog.article.Article;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "article_media")
public class ArticleMedia {
    @EmbeddedId
    private ArticleMediaId id;

    @MapsId("articleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @MapsId("mediaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;

    @Column(name = "display_name", length = 500)
    private String displayName;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ArticleMedia() {
    }

    public ArticleMedia(Article article, MediaAsset media, ArticleMediaRole role, String displayName,
                        Integer sortOrder, Instant createdAt) {
        this.id = new ArticleMediaId(article.getId(), media.getId(), role);
        this.article = article;
        this.media = media;
        this.displayName = displayName;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public ArticleMediaId getId() { return id; }
    public Article getArticle() { return article; }
    public MediaAsset getMedia() { return media; }
    public String getDisplayName() { return displayName; }
    public Integer getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    void updateAttachment(String displayName, int sortOrder) {
        if (id == null || id.getRole() != ArticleMediaRole.ATTACHMENT) {
            throw new IllegalStateException("Only attachment references can be reordered");
        }
        this.displayName = displayName;
        this.sortOrder = sortOrder;
    }
}
