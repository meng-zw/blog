ALTER TABLE media_asset
    ADD COLUMN location_hash BINARY(32)
      GENERATED ALWAYS AS (
        UNHEX(SHA2(CONCAT(provider, CHAR(0), bucket, CHAR(0), storage_key), 256))
      ) STORED,
    DROP INDEX uk_media_asset_location,
    ADD UNIQUE KEY uk_media_location_hash (location_hash);
