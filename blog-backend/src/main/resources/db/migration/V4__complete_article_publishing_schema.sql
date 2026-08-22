ALTER TABLE topic_article
    ADD CONSTRAINT uk_topic_article_single_article UNIQUE (article_id);

ALTER TABLE article
    CHANGE COLUMN html_content rendered_html LONGTEXT NULL,
    MODIFY COLUMN author_id BIGINT NULL,
    ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT 'ARTICLE' AFTER rendered_html,
    ADD COLUMN scheduled_at DATETIME(6) NULL AFTER published_at,
    ADD COLUMN topic_id BIGINT NULL AFTER category_id,
    ADD COLUMN seo_title VARCHAR(70) NULL AFTER scheduled_at,
    ADD COLUMN seo_description VARCHAR(160) NULL AFTER seo_title,
    ADD CONSTRAINT fk_article_topic FOREIGN KEY (topic_id) REFERENCES topic (id),
    ADD KEY idx_article_public_order (content_type, status, published_at, id),
    ADD KEY idx_article_topic (topic_id);

UPDATE article article_row
JOIN topic_article membership ON membership.article_id = article_row.id
SET article_row.topic_id = membership.topic_id;
