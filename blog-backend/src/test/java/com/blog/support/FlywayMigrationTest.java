package com.blog.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayMigrationTest extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migratesAnEmptyDatabaseToVersionEleven() {
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.info().current()).isNull();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(11);
        assertThat(result.targetSchemaVersion).isEqualTo("11");
    }

    @Test
    void supportsDurableDeletingStateAndItsRetryIndex() {
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO media_asset
                    (provider, bucket, storage_key, status, purpose, original_filename, content_type, byte_size,
                     created_at, updated_at)
                VALUES ('LOCAL', '', 'inline-images/deleting.png', 'DELETING', 'INLINE_IMAGE',
                        'deleting.png', 'image/png', 42, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """);

        assertThat(jdbc.queryForObject("SELECT status FROM media_asset WHERE storage_key = ?", String.class,
                "inline-images/deleting.png")).isEqualTo("DELETING");
        assertThat(jdbc.queryForList("SHOW INDEX FROM media_asset WHERE Key_name = 'idx_media_asset_status_id'"))
                .isNotEmpty();
    }

    @Test
    void articleMediaReferencesCascadeWithArticlesButKeepMediaAssetsProtected() {
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO admin_account (username, password_hash, display_name) VALUES (?, ?, ?)",
                "owner", "hash", "Owner");
        Long ownerId = jdbc.queryForObject("SELECT id FROM admin_account WHERE username = ?", Long.class, "owner");
        jdbc.update("""
                INSERT INTO media_asset
                    (provider, bucket, storage_key, status, purpose, original_filename, content_type, byte_size,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, "LOCAL", "", "inline-images/example.png", "READY", "INLINE_IMAGE", "example.png", "image/png", 42L);
        Long mediaId = jdbc.queryForObject("SELECT id FROM media_asset WHERE storage_key = ?", Long.class,
                "inline-images/example.png");
        jdbc.update("""
                INSERT INTO article (slug, title, summary, markdown_content, content_type, status, author_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, "media-reference", "Media reference", "Summary", "body", "ARTICLE", "DRAFT", ownerId);
        Long articleId = jdbc.queryForObject("SELECT id FROM article WHERE slug = ?", Long.class, "media-reference");
        jdbc.update("""
                INSERT INTO article_media (article_id, media_id, role, sort_order)
                VALUES (?, ?, ?, ?)
                """, articleId, mediaId, "INLINE", null);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM media_asset WHERE id = ?", mediaId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM article WHERE id = ?", articleId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article_media WHERE media_id = ?", Integer.class, mediaId))
                .isZero();
    }

    @Test
    void migratesVersionEightMediaRowsToTheLocalReadyInlineImageDefaults() {
        var versionEight = Flyway.configure().dataSource(dataSource).target("8").load();
        assertThat(versionEight.migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO admin_account (username, password_hash, display_name) VALUES (?, ?, ?)",
                "owner", "hash", "Owner");
        Long ownerId = jdbc.queryForObject("SELECT id FROM admin_account WHERE username = ?", Long.class, "owner");
        jdbc.update("""
                INSERT INTO media_asset (storage_key, original_filename, content_type, byte_size, uploaded_by_id)
                VALUES (?, ?, ?, ?, ?)
                """, "legacy/photo.png", "photo.png", "image/png", 42L, ownerId);

        var result = Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(3);
        var migrated = jdbc.queryForMap("""
                SELECT provider, bucket, status, purpose
                FROM media_asset
                WHERE storage_key = ?
                """, "legacy/photo.png");
        assertThat(migrated)
                .containsEntry("provider", "LOCAL")
                .containsEntry("bucket", "")
                .containsEntry("status", "READY")
                .containsEntry("purpose", "INLINE_IMAGE");
    }

    @Test
    void rejectsDuplicateLocalObjectLocationsAfterMigratingVersionEight() {
        var versionEight = Flyway.configure().dataSource(dataSource).target("8").load();
        assertThat(versionEight.migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO media_asset (storage_key, original_filename, content_type, byte_size)
                VALUES (?, ?, ?, ?)
                """, "legacy/photo.png", "photo.png", "image/png", 42L);
        assertThat(Flyway.configure().dataSource(dataSource).load().migrate().success).isTrue();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO media_asset
                    (provider, bucket, storage_key, status, purpose, original_filename, content_type, byte_size,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, "LOCAL", "", "legacy/photo.png", "READY", "INLINE_IMAGE", "duplicate.png", "image/png", 42L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @BeforeEach
    void resetDatabase() {
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
    }
}
