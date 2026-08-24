ALTER TABLE media_asset
    ADD COLUMN provider VARCHAR(20) NULL AFTER id,
    ADD COLUMN bucket VARCHAR(255) NULL AFTER provider,
    ADD COLUMN status VARCHAR(20) NULL AFTER storage_key,
    ADD COLUMN purpose VARCHAR(30) NULL AFTER status,
    ADD COLUMN etag VARCHAR(255) NULL AFTER height,
    ADD COLUMN confirmed_at DATETIME(6) NULL AFTER created_at,
    ADD COLUMN updated_at DATETIME(6) NULL AFTER confirmed_at;

UPDATE media_asset
SET provider = 'LOCAL',
    bucket = '',
    status = 'READY',
    purpose = 'INLINE_IMAGE',
    confirmed_at = created_at,
    updated_at = created_at;

ALTER TABLE media_asset
    MODIFY COLUMN provider VARCHAR(20) NOT NULL,
    MODIFY COLUMN bucket VARCHAR(255) NOT NULL,
    MODIFY COLUMN status VARCHAR(20) NOT NULL,
    MODIFY COLUMN purpose VARCHAR(30) NOT NULL,
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    DROP INDEX uk_media_asset_storage_key,
    ADD UNIQUE KEY uk_media_asset_location (provider, bucket(191), storage_key),
    ADD KEY idx_media_asset_status_created_at (status, created_at),
    ADD KEY idx_media_asset_uploaded_by_status (uploaded_by_id, status);
