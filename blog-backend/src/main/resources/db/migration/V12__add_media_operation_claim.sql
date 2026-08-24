-- Durable claims serialize upload verification/proxy writes with background cleanup without
-- holding database row locks while object storage performs network I/O.
ALTER TABLE media_asset
    ADD COLUMN operation_token VARCHAR(36) NULL AFTER uploaded_by_id;
