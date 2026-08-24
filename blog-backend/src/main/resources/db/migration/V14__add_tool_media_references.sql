CREATE TABLE tool_media (
    tool_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tool_id, media_id),
    KEY idx_tool_media_media_id (media_id),
    KEY idx_tool_media_tool_order (tool_id, sort_order),
    CONSTRAINT fk_tool_media_tool FOREIGN KEY (tool_id) REFERENCES tool (id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_media_media FOREIGN KEY (media_id) REFERENCES media_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
