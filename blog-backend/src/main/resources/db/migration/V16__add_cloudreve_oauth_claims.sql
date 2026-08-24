ALTER TABLE cloudreve_connection
    ADD COLUMN refresh_claim_token VARCHAR(64) NULL AFTER status,
    ADD COLUMN refresh_claimed_at DATETIME(6) NULL AFTER refresh_claim_token,
    ADD COLUMN authorization_generation BIGINT NOT NULL DEFAULT 0 AFTER refresh_claimed_at;
