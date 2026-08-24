package com.blog.media;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ToolMediaId implements Serializable {
    private Long toolId;
    private Long mediaId;

    protected ToolMediaId() {
    }

    public ToolMediaId(Long toolId, Long mediaId) {
        this.toolId = toolId;
        this.mediaId = mediaId;
    }

    public Long getToolId() { return toolId; }
    public Long getMediaId() { return mediaId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolMediaId that)) return false;
        return Objects.equals(toolId, that.toolId) && Objects.equals(mediaId, that.mediaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolId, mediaId);
    }
}
