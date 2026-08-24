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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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
    void migratesAFreshMySqlDatabaseThroughLocationHashIdentity() {
        assumeDockerAvailable();
        cleanDatabase();

        var result = flyway().migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("13");
        assertThat(indexColumns("media_asset", "uk_media_location_hash"))
                .containsExactly("location_hash");
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
        assertThat(indexColumns("media_asset", "uk_media_location_hash"))
                .containsExactly("location_hash");
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

    private static List<String> indexColumns(String table, String index) {
        return new ArrayList<>(jdbc().queryForList("""
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                ORDER BY seq_in_index
                """, String.class, table, index));
    }
}
