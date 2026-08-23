ALTER TABLE tool
    CHANGE COLUMN logo_media_id cover_media_id BIGINT NULL,
    ALGORITHM=INPLACE;

ALTER TABLE tool
    CHANGE COLUMN description description_markdown LONGTEXT NULL,
    CHANGE COLUMN url official_url VARCHAR(1000) NULL,
    MODIFY COLUMN author_id BIGINT NULL,
    ADD COLUMN summary TEXT NULL AFTER name,
    ADD COLUMN rendered_html LONGTEXT NULL AFTER description_markdown,
    ADD COLUMN featured BIT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER featured,
    ADD KEY idx_tool_public_order (status, featured, sort_order, published_at, id),
    ADD KEY idx_tool_category (category_id);
