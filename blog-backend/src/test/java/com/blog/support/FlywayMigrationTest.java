package com.blog.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayMigrationTest extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migratesAnEmptyDatabaseToVersionFifteen() {
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.info().current()).isNull();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(16);
        assertThat(result.targetSchemaVersion).isEqualTo("15");
    }

    @Test
    void createsOneEncryptedCloudreveConnectionRecord() {
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'cloudreve_connection'
                """).stream().map(row -> row.get("column_name")))
                .contains("singleton_key", "access_token_ciphertext", "access_token_nonce", "access_token_expires_at",
                        "refresh_token_ciphertext", "refresh_token_nonce", "refresh_token_expires_at", "granted_scopes",
                        "status", "version", "created_at", "updated_at")
                .doesNotContain("client_secret", "token_encryption_key");

        jdbc.update("""
                INSERT INTO cloudreve_connection
                    (singleton_key, access_token_ciphertext, access_token_nonce, refresh_token_ciphertext,
                     refresh_token_nonce, status)
                VALUES (1, X'0102', X'0102030405060708090A0B0C', X'0304', X'0D0E0F101112131415161718', 'CONNECTED')
                """);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO cloudreve_connection (singleton_key, status)
                VALUES (1, 'DISCONNECTED')
                """)).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO cloudreve_connection (singleton_key, status)
                VALUES (2, 'DISCONNECTED')
                """)).isInstanceOf(DataIntegrityViolationException.class);
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
    void toolMediaReferencesCascadeWithToolsButKeepMediaAssetsProtected() {
        var flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO media_asset
                    (provider, bucket, storage_key, status, purpose, original_filename, content_type, byte_size,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, "LOCAL", "", "inline-images/tool-example.png", "READY", "INLINE_IMAGE", "tool-example.png",
                "image/png", 42L);
        Long mediaId = jdbc.queryForObject("SELECT id FROM media_asset WHERE storage_key = ?", Long.class,
                "inline-images/tool-example.png");
        jdbc.update("""
                INSERT INTO tool (slug, name, summary, description_markdown, official_url, status, featured, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, "tool-media-reference", "Tool media reference", "Summary", "body", "https://example.com", "DRAFT", false, 0);
        Long toolId = jdbc.queryForObject("SELECT id FROM tool WHERE slug = ?", Long.class, "tool-media-reference");
        jdbc.update("INSERT INTO tool_media (tool_id, media_id, sort_order) VALUES (?, ?, ?)", toolId, mediaId, 0);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM media_asset WHERE id = ?", mediaId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM tool WHERE id = ?", toolId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_media WHERE media_id = ?", Integer.class, mediaId))
                .isZero();
    }

    @Test
    void backfillsPublishedAndArchivedToolMarkdownWithoutPromotingInvalidLegacyReferences() {
        assertThat(Flyway.configure().dataSource(dataSource).target("14").load().migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long first = insertMedia(jdbc, "backfill-first.png", "READY", "INLINE_IMAGE");
        Long second = insertMedia(jdbc, "backfill-second.png", "READY", "INLINE_IMAGE");
        Long pending = insertMedia(jdbc, "backfill-pending.png", "PENDING_UPLOAD", "INLINE_IMAGE");
        Long attachment = insertMedia(jdbc, "backfill-attachment.pdf", "READY", "ATTACHMENT");
        Long published = insertTool(jdbc, "backfill-published", "PUBLISHED", """
                ![second](/api/media/assets/%d)
                ![duplicate](/api/media/assets/%d)
                ```md
                ![fenced](/api/media/assets/%d)
                ```
                ![pending](/api/media/assets/%d)
                ![attachment](/api/media/assets/%d)
                ![missing](/api/media/assets/999999)
                ![query](/api/media/assets/%d?legacy=1)
                ![first](/api/media/assets/%d)
                """.formatted(second, second, first, pending, attachment, first, first));
        Long archived = insertTool(jdbc, "backfill-archived", "ARCHIVED",
                "![first](/api/media/assets/" + first + ")");

        var result = Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("15");
        List<ToolMediaRow> rows = jdbc.query("""
                SELECT tool_id, media_id, sort_order
                FROM tool_media
                ORDER BY tool_id, sort_order
                """, (resultSet, rowNum) -> new ToolMediaRow(resultSet.getLong("tool_id"),
                resultSet.getLong("media_id"), resultSet.getInt("sort_order")));
        assertThat(rows).containsExactly(
                new ToolMediaRow(published, second, 0),
                new ToolMediaRow(published, first, 1),
                new ToolMediaRow(archived, first, 0));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM media_asset WHERE id = ?", first))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_media WHERE media_id IN (?, ?)", Integer.class,
                pending, attachment)).isZero();
    }

    @Test
    void backfillUsesMoreThanOnePageAndKeepsPreexistingToolMediaRowsIdempotently() {
        assertThat(Flyway.configure().dataSource(dataSource).target("14").load().migrate().success).isTrue();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long mediaId = insertMedia(jdbc, "backfill-paged.png", "READY", "INLINE_IMAGE");
        List<Long> toolIds = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> insertTool(jdbc, "backfill-page-" + index, "DRAFT",
                        "![image](/api/media/assets/" + mediaId + ")"))
                .toList();
        jdbc.update("INSERT INTO tool_media (tool_id, media_id, sort_order) VALUES (?, ?, ?)", toolIds.getFirst(),
                mediaId, 37);

        assertThat(Flyway.configure().dataSource(dataSource).load().migrate().success).isTrue();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_media WHERE media_id = ?", Integer.class, mediaId))
                .isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT sort_order FROM tool_media WHERE tool_id = ? AND media_id = ?",
                Integer.class, toolIds.getFirst(), mediaId)).isEqualTo(37);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_media WHERE media_id = ? AND sort_order = 0",
                Integer.class, mediaId)).isEqualTo(10);
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
        assertThat(result.migrationsExecuted).isEqualTo(7);
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

    private static Long insertMedia(JdbcTemplate jdbc, String filename, String status, String purpose) {
        jdbc.update("""
                INSERT INTO media_asset
                    (provider, bucket, storage_key, status, purpose, original_filename, content_type, byte_size,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, "LOCAL", "", "inline-images/" + filename, status, purpose, filename,
                purpose.equals("ATTACHMENT") ? "application/pdf" : "image/png", 42L);
        return jdbc.queryForObject("SELECT id FROM media_asset WHERE storage_key = ?", Long.class,
                "inline-images/" + filename);
    }

    private static Long insertTool(JdbcTemplate jdbc, String slug, String status, String markdown) {
        jdbc.update("""
                INSERT INTO tool
                    (slug, name, summary, description_markdown, official_url, status, featured, sort_order, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                """, slug, slug, "Summary", markdown, "https://example.com", status, false, 0);
        return jdbc.queryForObject("SELECT id FROM tool WHERE slug = ?", Long.class, slug);
    }

    private record ToolMediaRow(long toolId, long mediaId, int sortOrder) {
    }
}
