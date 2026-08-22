ALTER TABLE article DROP FOREIGN KEY fk_article_author;
ALTER TABLE article DROP COLUMN author_id;

UPDATE article SET html_content = '' WHERE html_content IS NULL;
ALTER TABLE article CHANGE COLUMN html_content rendered_html LONGTEXT NOT NULL;

UPDATE article SET summary = LEFT(title, 500) WHERE summary IS NULL OR TRIM(summary) = '';
ALTER TABLE article
    MODIFY COLUMN title VARCHAR(200) NOT NULL,
    MODIFY COLUMN slug VARCHAR(160) NOT NULL,
    MODIFY COLUMN summary VARCHAR(500) NOT NULL;

ALTER TABLE article
    ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT 'ARTICLE' AFTER rendered_html,
    ADD COLUMN scheduled_at DATETIME(6) NULL AFTER published_at,
    ADD COLUMN topic_id BIGINT NULL AFTER category_id,
    ADD COLUMN seo_title VARCHAR(70) NULL AFTER scheduled_at,
    ADD COLUMN seo_description VARCHAR(160) NULL AFTER seo_title,
    ADD CONSTRAINT fk_article_topic FOREIGN KEY (topic_id) REFERENCES topic (id),
    ADD KEY idx_article_public_order (content_type, status, published_at, id),
    ADD KEY idx_article_topic (topic_id);
