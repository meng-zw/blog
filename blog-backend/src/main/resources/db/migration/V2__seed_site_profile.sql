ALTER TABLE site_profile
    ADD COLUMN subtitle VARCHAR(160) NOT NULL DEFAULT '' AFTER site_name,
    ADD COLUMN avatar_media_id BIGINT NULL AFTER avatar_url,
    ADD CONSTRAINT fk_site_profile_avatar_media FOREIGN KEY (avatar_media_id) REFERENCES media_asset (id);

INSERT INTO site_profile (site_name, subtitle, site_description, owner_name, avatar_url, github_url)
VALUES ('小M的思与行', '中庸之道', '中庸之道', '小M', NULL, 'https://github.com/meng-zw');
