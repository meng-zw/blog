package com.blog.article;

import com.blog.media.MediaAsset;
import com.blog.shared.persistence.AuditedEntity;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.Tag;
import com.blog.topic.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity(name = "PublishingArticle")
@Table(name = "article")
public class Article extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "markdown_content", nullable = false, columnDefinition = "LONGTEXT")
    private String markdownContent;

    @Column(name = "rendered_html", columnDefinition = "LONGTEXT")
    private String renderedHtml;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_media_id")
    private MediaAsset coverMedia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "article_tag", joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "seo_title", length = 70)
    private String seoTitle;

    @Column(name = "seo_description", length = 160)
    private String seoDescription;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getMarkdownContent() { return markdownContent; }
    public void setMarkdownContent(String markdownContent) { this.markdownContent = markdownContent; }
    public String getRenderedHtml() { return renderedHtml; }
    public void setRenderedHtml(String renderedHtml) { this.renderedHtml = renderedHtml; }
    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }
    public ArticleStatus getStatus() { return status; }
    public void setStatus(ArticleStatus status) { this.status = status; }
    public MediaAsset getCoverMedia() { return coverMedia; }
    public void setCoverMedia(MediaAsset coverMedia) { this.coverMedia = coverMedia; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Topic getTopic() { return topic; }
    public void setTopic(Topic topic) { this.topic = topic; }
    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = new LinkedHashSet<>(tags); }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getSeoTitle() { return seoTitle; }
    public void setSeoTitle(String seoTitle) { this.seoTitle = seoTitle; }
    public String getSeoDescription() { return seoDescription; }
    public void setSeoDescription(String seoDescription) { this.seoDescription = seoDescription; }

    public boolean isVisibleAt(Instant now) {
        return status == ArticleStatus.PUBLISHED && publishedAt != null && !publishedAt.isAfter(now);
    }
}
