package com.blog.media;

import com.blog.tool.Tool;
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
@Table(name = "tool_media")
public class ToolMedia {
    @EmbeddedId
    private ToolMediaId id;

    @MapsId("toolId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_id", nullable = false)
    private Tool tool;

    @MapsId("mediaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ToolMedia() {
    }

    public ToolMedia(Tool tool, MediaAsset media, int sortOrder, Instant createdAt) {
        this.id = new ToolMediaId(tool.getId(), media.getId());
        this.tool = tool;
        this.media = media;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public ToolMediaId getId() { return id; }
    public Tool getTool() { return tool; }
    public MediaAsset getMedia() { return media; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
