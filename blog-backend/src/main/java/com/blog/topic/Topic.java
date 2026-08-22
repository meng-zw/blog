package com.blog.topic;

import com.blog.media.MediaAsset;
import com.blog.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "topic")
public class Topic extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 160, unique = true)
    private String normalizedName;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_media_id")
    private MediaAsset coverMedia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TopicStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public MediaAsset getCoverMedia() { return coverMedia; }
    public void setCoverMedia(MediaAsset coverMedia) { this.coverMedia = coverMedia; }
    public TopicStatus getStatus() { return status; }
    public void setStatus(TopicStatus status) { this.status = status; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
