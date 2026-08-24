ALTER TABLE media_asset
    ADD COLUMN location_hash BINARY(32)
      GENERATED ALWAYS AS (
        UNHEX(SHA2(CONCAT(
          OCTET_LENGTH(provider), ':', provider,
          OCTET_LENGTH(bucket), ':', bucket,
          OCTET_LENGTH(storage_key), ':', storage_key
        ), 256))
      ) STORED,
    DROP INDEX uk_media_asset_location,
    ADD UNIQUE KEY uk_media_location_hash (location_hash);
