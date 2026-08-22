package com.blog.article;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ArticleMigrationMySqlIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void populatedV1ToV3UpgradePreservesMaximumLegacyArticleDataAndAddsSingleTopicSemantics() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("3").load().migrate();
        String title = "t".repeat(255);
        String slug = "s".repeat(255);
        String summary = "summary-" + "x".repeat(2000);
        String html = "<h1>legacy rendered content</h1>";

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("insert into admin_account(username,password_hash,display_name,enabled) values ('admin','x','Admin',1)");
                statement.executeUpdate("insert into topic(name,normalized_name,slug,status,sort_order) values ('Series','series','series','PUBLISHED',0)");
            }
            try (var insert = connection.prepareStatement("insert into article(title,slug,summary,markdown_content,html_content,status,author_id) values (?,?,?,?,?,'DRAFT',1)")) {
                insert.setString(1, title);
                insert.setString(2, slug);
                insert.setString(3, summary);
                insert.setString(4, "# legacy markdown");
                insert.setString(5, html);
                insert.executeUpdate();
            }
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("insert into topic_article(topic_id,article_id,sort_order) values (1,1,0)");
            }
        }

        var result = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load().migrate();
        assertThat(result.targetSchemaVersion).isEqualTo("4");

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            var article = statement.executeQuery("select title,slug,summary,rendered_html,author_id,topic_id from article where id=1");
            assertThat(article.next()).isTrue();
            assertThat(article.getString("title")).isEqualTo(title);
            assertThat(article.getString("slug")).isEqualTo(slug);
            assertThat(article.getString("summary")).isEqualTo(summary);
            assertThat(article.getString("rendered_html")).isEqualTo(html);
            assertThat(article.getLong("author_id")).isEqualTo(1L);
            assertThat(article.getLong("topic_id")).isEqualTo(1L);

            statement.executeUpdate("insert into article(title,slug,summary,markdown_content,rendered_html,status,content_type) "
                    + "values ('new','new','new summary','# new','<h1>new</h1>','DRAFT','ARTICLE')");
            statement.executeUpdate("insert into topic(name,normalized_name,slug,status,sort_order) values ('Other','other','other','PUBLISHED',1)");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "insert into topic_article(topic_id,article_id,sort_order) values (2,1,0)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
