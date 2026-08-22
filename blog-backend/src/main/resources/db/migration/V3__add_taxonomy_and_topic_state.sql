ALTER TABLE category
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'ARTICLE' AFTER sort_order,
    ADD COLUMN normalized_name VARCHAR(120) NOT NULL AFTER name,
    ADD CONSTRAINT uk_category_normalized_name UNIQUE (normalized_name);

ALTER TABLE tag
    ADD COLUMN normalized_name VARCHAR(120) NOT NULL AFTER name,
    ADD CONSTRAINT uk_tag_normalized_name UNIQUE (normalized_name);

ALTER TABLE topic
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' AFTER cover_media_id,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN normalized_name VARCHAR(160) NOT NULL AFTER name,
    ADD CONSTRAINT uk_topic_normalized_name UNIQUE (normalized_name);

CREATE TABLE taxonomy_slug_lock (
    id BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO taxonomy_slug_lock (id) VALUES (1);
