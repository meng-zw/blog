-- status is intentionally VARCHAR, so DELETING is forward-compatible without an enum rewrite.
-- This index bounds and accelerates the background retry scan introduced with two-phase deletion.
ALTER TABLE media_asset
    ADD KEY idx_media_asset_status_id (status, updated_at, id);
