package com.blog.tool;

import com.blog.media.MediaAsset;
import com.blog.shared.persistence.AuditedEntity;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.Tag;
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

/** 提效工具聚合根，支持精选排序、分类标签和 Markdown 说明。 */
@Entity(name = "PublishingTool")
@Table(name = "tool")
public class Tool extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "description_markdown", columnDefinition = "LONGTEXT")
    private String descriptionMarkdown;

    @Column(name = "rendered_html", columnDefinition = "LONGTEXT")
    private String renderedHtml;

    @Column(name = "official_url", length = 1000)
    private String officialUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_media_id")
    private MediaAsset coverMedia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tool_tag", joinColumns = @JoinColumn(name = "tool_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    /** 工具的发布状态；公开查询必须通过 isVisibleAt 判断。 */
    private ToolStatus status;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "published_at")
    private Instant publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescriptionMarkdown() { return descriptionMarkdown; }
    public void setDescriptionMarkdown(String descriptionMarkdown) { this.descriptionMarkdown = descriptionMarkdown; }
    public String getRenderedHtml() { return renderedHtml; }
    public void setRenderedHtml(String renderedHtml) { this.renderedHtml = renderedHtml; }
    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String officialUrl) { this.officialUrl = officialUrl; }
    public MediaAsset getCoverMedia() { return coverMedia; }
    public void setCoverMedia(MediaAsset coverMedia) { this.coverMedia = coverMedia; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = new LinkedHashSet<>(tags); }
    public ToolStatus getStatus() { return status; }
    public void setStatus(ToolStatus status) { this.status = status; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    /** 防止未来定时发布的工具提前出现在公开页面。 */
    public boolean isVisibleAt(Instant now) {
        return status == ToolStatus.PUBLISHED && publishedAt != null && !publishedAt.isAfter(now);
    }
}
