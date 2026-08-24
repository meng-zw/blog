package com.blog.media;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MediaMigrationMySqlIntegrationTest {
    private static final int INNODB_MAX_INDEX_BYTES = 3_072;
    private static final int UTF8MB4_MAX_BYTES_PER_CHARACTER = 4;
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static boolean dockerAvailable;

    @BeforeAll
    static void startMySqlWhenDockerIsAvailable() {
        dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        if (dockerAvailable) {
            MYSQL.start();
        }
    }

    @AfterAll
    static void stopMySqlWhenStarted() {
        if (MYSQL.isRunning()) {
            MYSQL.stop();
        }
    }

    @Test
    void bootstrapLocationIndexFitsTheInnoDbUtf8mb4KeyLimit() throws IOException {
        String schema = Files.readString(MIGRATIONS.resolve("V1__create_personal_blog_schema.sql"))
                + Files.readString(MIGRATIONS.resolve("V9__add_pluggable_media_storage.sql"));

        assertThat(declaredLocationIndexWidth(schema))
                .as("maximum utf8mb4 bytes declared by uk_media_asset_location")
                .isLessThanOrEqualTo(INNODB_MAX_INDEX_BYTES);
    }

    @Test
    void locationHashLengthPrefixesEveryIdentityComponent() throws IOException {
        String migration = Files.readString(MIGRATIONS.resolve("V13__harden_media_location_identity.sql"))
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);

        assertThat(migration)
                .contains("octet_length(provider), ':', provider")
                .contains("octet_length(bucket), ':', bucket")
                .contains("octet_length(storage_key), ':', storage_key");
    }

    @Test
    void migratesAFreshMySqlDatabaseThroughToolMediaBackfill() {
        assumeDockerAvailable();
        cleanDatabase();

        var result = flyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("15");
        assertLocationIdentityContract();
        assertLengthPrefixedIdentityAvoidsDelimiterCollisions();
    }

    @Test
    void migratesVersionEightMediaDataThroughLocationHashIdentity() {
        assumeDockerAvailable();
        cleanDatabase();
        assertThat(Flyway.configure().dataSource(dataSource()).target("8").load().migrate().success).isTrue();
        JdbcTemplate jdbc = jdbc();
        jdbc.update("""
                INSERT INTO media_asset (storage_key, original_filename, content_type, byte_size)
                VALUES (?, ?, ?, ?)
                """, "legacy/photo.png", "photo.png", "image/png", 42L);

        var result = flyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE storage_key = ? AND location_hash IS NOT NULL",
                Integer.class, "legacy/photo.png")).isOne();
        assertLocationIdentityContract();
        assertDuplicateLocationIsRejected();
    }

    private static int declaredLocationIndexWidth(String schema) {
        Map<String, Integer> varcharWidths = new LinkedHashMap<>();
        Matcher columns = Pattern.compile("(?i)\\b(provider|bucket|storage_key)\\s+VARCHAR\\((\\d+)\\)")
                .matcher(schema);
        while (columns.find()) {
            varcharWidths.put(columns.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(columns.group(2)));
        }
        Matcher index = Pattern.compile("(?is)uk_media_asset_location\\s+(?:UNIQUE\\s*)?\\(((?:[^()]|\\([^)]*\\))*)\\)")
                .matcher(schema);
        assertThat(index.find()).as("uk_media_asset_location declaration").isTrue();
        int characters = 0;
        for (String indexedColumn : index.group(1).split(",")) {
            Matcher part = Pattern.compile("(?i)\\s*([a-z_]+)(?:\\((\\d+)\\))?\\s*").matcher(indexedColumn);
            assertThat(part.matches()).as("index column declaration %s", indexedColumn).isTrue();
            String name = part.group(1).toLowerCase(Locale.ROOT);
            Integer declaredWidth = varcharWidths.get(name);
            assertThat(declaredWidth).as("VARCHAR width for %s", name).isNotNull();
            characters += part.group(2) == null ? declaredWidth : Integer.parseInt(part.group(2));
        }
        return characters * UTF8MB4_MAX_BYTES_PER_CHARACTER;
    }

    private static void assumeDockerAvailable() {
        assumeTrue(dockerAvailable, "Docker is required for the MySQL migration integration gate");
    }

    private static Flyway flyway() {
        return Flyway.configure().dataSource(dataSource()).load();
    }

    private static void cleanDatabase() {
        Flyway.configure().dataSource(dataSource()).cleanDisabled(false).load().clean();
    }

    private static DataSource dataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource());
    }

    private static void assertLocationIdentityContract() {
        List<Map<String, Object>> index = jdbc().queryForList("""
                SELECT column_name, non_unique
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'media_asset'
                  AND index_name = 'uk_media_location_hash'
                ORDER BY seq_in_index
                """);
        assertThat(index).extracting(row -> row.get("column_name")).containsExactly("location_hash");
        assertThat(index).extracting(row -> ((Number) row.get("non_unique")).intValue()).containsExactly(0);

        Map<String, Object> generated = jdbc().queryForMap("""
                SELECT data_type, character_octet_length, generation_expression, extra
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'media_asset' AND column_name = 'location_hash'
                """);
        assertThat(generated.get("data_type")).isEqualTo("binary");
        assertThat(((Number) generated.get("character_octet_length")).intValue()).isEqualTo(32);
        assertThat(generated.get("extra").toString().toLowerCase(Locale.ROOT)).contains("stored generated");
        assertThat(generated.get("generation_expression").toString().toLowerCase(Locale.ROOT))
                .contains("sha2")
                .contains("octet_length");
    }

    private static void assertLengthPrefixedIdentityAvoidsDelimiterCollisions() {
        insertReadyLocation("R2", "a\0b", "c", "first.png");
        insertReadyLocation("R2", "a", "b\0c", "second.png");

        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM media_asset WHERE original_filename IN (?, ?)",
                Integer.class, "first.png", "second.png")).isEqualTo(2);
        assertThatThrownBy(() -> insertReadyLocation("R2", "a\0b", "c", "duplicate.png"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private static void assertDuplicateLocationIsRejected() {
        insertReadyLocation("R2", "archive", "inline-images/same.png", "first.png");

        assertThatThrownBy(() -> insertReadyLocation("R2", "archive", "inline-images/same.png", "duplicate.png"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private static void insertReadyLocation(String provider, String bucket, String key, String filename) {
        jdbc().update("""
                INSERT INTO media_asset
                    (provider, bucket, storage_key, status, purpose, original_filename, content_type, byte_size,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'READY', 'INLINE_IMAGE', ?, 'image/png', 7,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, provider, bucket, key, filename);
    }
}
