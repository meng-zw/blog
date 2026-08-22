package com.blog.taxonomy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class TaxonomyTopicMySqlIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void migrationsEnforceFoldedKeysRestrictTagReferencesAndExposeTopicAssociationOrder() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into category(name, normalized_name, slug, sort_order, scope) values ('Straße', 'strasse', 'strasse', 0, 'ARTICLE')");
            assertThatThrownBy(() -> statement.executeUpdate("insert into category(name, normalized_name, slug, sort_order, scope) values ('STRASSE', 'strasse', 'strasse-2', 0, 'ARTICLE')"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.executeUpdate("insert into tag(name, normalized_name, slug) values ('Tag', 'tag', 'tag')");
            statement.executeUpdate("insert into admin_account(username,password_hash,display_name,enabled) values ('admin','x','Admin',1)");
            statement.executeUpdate("insert into article(title,slug,summary,markdown_content,rendered_html,content_type,status) "
                    + "values ('A','a','Summary','x','<p>x</p>','ARTICLE','DRAFT')");
            statement.executeUpdate("insert into article_tag(article_id,tag_id) values (1,1)");
            assertThatThrownBy(() -> statement.executeUpdate("delete from tag where id=1")).isInstanceOf(java.sql.SQLException.class);
            statement.executeUpdate("insert into topic(name,normalized_name,slug,status,sort_order) values ('T','t','t','PUBLISHED',0)");
            statement.executeUpdate("insert into topic_article(topic_id,article_id,sort_order) values (1,1,0)");
            var result = statement.executeQuery("select sort_order from topic_article where topic_id=1 and article_id=1");
            result.next();
            org.assertj.core.api.Assertions.assertThat(result.getInt(1)).isZero();
        }
    }
}
