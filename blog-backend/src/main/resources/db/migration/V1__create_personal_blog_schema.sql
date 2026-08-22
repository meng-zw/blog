CREATE TABLE admin_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    enabled_guard TINYINT GENERATED ALWAYS AS (CASE WHEN enabled = 1 THEN 1 ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_account_username (username),
    UNIQUE KEY uk_admin_account_enabled_guard (enabled_guard)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE site_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    site_name VARCHAR(160) NOT NULL,
    site_description TEXT NULL,
    owner_name VARCHAR(120) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    github_url VARCHAR(500) NULL,
    email VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE media_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INT NULL,
    height INT NULL,
    uploaded_by_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_asset_storage_key (storage_key),
    CONSTRAINT fk_media_asset_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES admin_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    description TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    description TEXT NULL,
    cover_media_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_slug (slug),
    CONSTRAINT fk_topic_cover_media FOREIGN KEY (cover_media_id) REFERENCES media_asset (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE article (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    summary TEXT NULL,
    markdown_content LONGTEXT NOT NULL,
    html_content LONGTEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    cover_media_id BIGINT NULL,
    category_id BIGINT NULL,
    author_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_slug (slug),
    KEY idx_article_status_published_at (status, published_at),
    CONSTRAINT fk_article_cover_media FOREIGN KEY (cover_media_id) REFERENCES media_asset (id),
    CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_article_author FOREIGN KEY (author_id) REFERENCES admin_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE article_tag (
    article_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_article_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE topic_article (
    topic_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (topic_id, article_id),
    CONSTRAINT fk_topic_article_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_article_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tool (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    description TEXT NULL,
    url VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(6) NULL,
    logo_media_id BIGINT NULL,
    category_id BIGINT NULL,
    author_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_slug (slug),
    KEY idx_tool_status_published_at (status, published_at),
    CONSTRAINT fk_tool_logo_media FOREIGN KEY (logo_media_id) REFERENCES media_asset (id),
    CONSTRAINT fk_tool_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_tool_author FOREIGN KEY (author_id) REFERENCES admin_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tool_tag (
    tool_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (tool_id, tag_id),
    CONSTRAINT fk_tool_tag_tool FOREIGN KEY (tool_id) REFERENCES tool (id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
