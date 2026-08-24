CREATE TABLE article_media (
    article_id BIGINT NOT NULL,
    media_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    display_name VARCHAR(500) NULL,
    sort_order INT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (article_id, media_id, role),
    KEY idx_article_media_media_id (media_id),
    KEY idx_article_media_article_role_order (article_id, role, sort_order),
    CONSTRAINT fk_article_media_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_media_media FOREIGN KEY (media_id) REFERENCES media_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
