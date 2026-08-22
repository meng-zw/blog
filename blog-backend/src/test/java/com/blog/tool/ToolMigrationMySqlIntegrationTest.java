package com.blog.tool;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

@Testcontainers(disabledWithoutDocker = true)
class ToolMigrationMySqlIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void populatedV1ToolUpgradePreservesLegacyValuesAndAllowsNullLegacyAuthor() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("1").load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into admin_account(username,password_hash,display_name,enabled) "
                    + "values ('admin','x','Admin',1)");
            statement.executeUpdate("insert into media_asset(storage_key,original_filename,content_type,byte_size) "
                    + "values ('legacy.png','legacy.png','image/png',10)");
            statement.executeUpdate("insert into tool(name,slug,description,url,status,logo_media_id,author_id) values "
                    + "('Legacy Tool','legacy-tool','legacy markdown','https://legacy.example','DRAFT',1,1)");
        }

        var result = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load().migrate();
        Assertions.assertThat(result.targetSchemaVersion).isEqualTo("7");

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            var legacy = statement.executeQuery("select name,slug,description_markdown,official_url,cover_media_id,summary "
                    + "from tool where id=1");
            Assertions.assertThat(legacy.next()).isTrue();
            Assertions.assertThat(legacy.getString("name")).isEqualTo("Legacy Tool");
            Assertions.assertThat(legacy.getString("slug")).isEqualTo("legacy-tool");
            Assertions.assertThat(legacy.getString("description_markdown")).isEqualTo("legacy markdown");
            Assertions.assertThat(legacy.getString("official_url")).isEqualTo("https://legacy.example");
            Assertions.assertThat(legacy.getLong("cover_media_id")).isEqualTo(1L);
            Assertions.assertThat(legacy.getString("summary")).isNull();

            statement.executeUpdate("insert into tool(name,slug,description_markdown,official_url,status,featured,sort_order) "
                    + "values ('No Author','no-author','body','https://example.com','DRAFT',0,0)");
        }
    }
}
