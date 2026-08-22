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
    void populatedV1ToV3UpgradeReconcilesMultiTopicDataAndPreservesLegacyArticleContent() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("3").load().migrate();
        String title = "t".repeat(255);
        String slug = "s".repeat(255);
        String summary = "summary-" + "x".repeat(2000);
        String html = "<h1>legacy rendered content</h1>";

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("insert into admin_account(username,password_hash,display_name,enabled) values ('admin','x','Admin',1)");
                statement.executeUpdate("insert into topic(name,normalized_name,slug,status,sort_order) values "
                        + "('Alpha','alpha','alpha','PUBLISHED',0),"
                        + "('Beta','beta','beta','PUBLISHED',1),"
                        + "('Gamma','gamma','gamma','PUBLISHED',2)");
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
                statement.executeUpdate("insert into article(title,slug,summary,markdown_content,html_content,status,author_id) values "
                        + "('Second','second','Second summary','# second','<p>second</p>','DRAFT',1),"
                        + "('Third','third','Third summary','# third','<p>third</p>','DRAFT',1),"
                        + "('Tie','tie','Tie summary','# tie','<p>tie</p>','DRAFT',1)");
                statement.executeUpdate("insert into topic_article(topic_id,article_id,sort_order) values "
                        + "(1,1,5),(2,1,1),(2,2,9),(1,3,8),(2,4,4),(3,4,4)");
            }
        }

        var result = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load().migrate();
        assertThat(result.targetSchemaVersion).isEqualTo("7");

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            var article = statement.executeQuery("select title,slug,summary,rendered_html,author_id,topic_id from article where id=1");
            assertThat(article.next()).isTrue();
            assertThat(article.getString("title")).isEqualTo(title);
            assertThat(article.getString("slug")).isEqualTo(slug);
            assertThat(article.getString("summary")).isEqualTo(summary);
            assertThat(article.getString("rendered_html")).isEqualTo(html);
            assertThat(article.getLong("author_id")).isEqualTo(1L);
            assertThat(article.getLong("topic_id")).isEqualTo(2L);

            assertThat(topicId(statement, 1L)).isEqualTo(2L);
            assertThat(topicId(statement, 4L)).isEqualTo(2L);
            assertThat(projectionCount(statement, 1L)).isEqualTo(1);
            assertThat(projectionCount(statement, 4L)).isEqualTo(1);
            assertThat(orderedMemberships(statement, 1L)).containsExactly("3:0");
            assertThat(orderedMemberships(statement, 2L)).containsExactly("1:0", "4:1", "2:2");
            assertThat(orderedMemberships(statement, 3L)).isEmpty();

            statement.executeUpdate("insert into article(title,slug,summary,markdown_content,rendered_html,status,content_type) "
                    + "values ('new','new','new summary','# new','<h1>new</h1>','DRAFT','ARTICLE')");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "insert into topic_article(topic_id,article_id,sort_order) values (3,1,0)"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    private static long topicId(java.sql.Statement statement, long articleId) throws Exception {
        var result = statement.executeQuery("select topic_id from article where id=" + articleId);
        assertThat(result.next()).isTrue();
        return result.getLong(1);
    }

    private static int projectionCount(java.sql.Statement statement, long articleId) throws Exception {
        var result = statement.executeQuery("select count(*) from topic_article where article_id=" + articleId);
        assertThat(result.next()).isTrue();
        return result.getInt(1);
    }

    private static java.util.List<String> orderedMemberships(java.sql.Statement statement, long topicId)
            throws Exception {
        var result = statement.executeQuery("select article_id,sort_order from topic_article where topic_id="
                + topicId + " order by sort_order,article_id");
        java.util.List<String> memberships = new java.util.ArrayList<>();
        while (result.next()) {
            memberships.add(result.getLong(1) + ":" + result.getInt(2));
        }
        return memberships;
    }
}
