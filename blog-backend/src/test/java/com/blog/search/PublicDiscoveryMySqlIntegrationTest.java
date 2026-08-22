package com.blog.search;

import com.blog.search.dto.SearchResultType;
import com.blog.site.SitemapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PublicDiscoveryMySqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired SearchRepository searchRepository;
    @Autowired SitemapService sitemapService;

    @Test
    void unionSearchHasStableCrossTypePagesAndExcludesEveryInvisibleLifecycle() {
        article("search-visible-article", "target", "visible", "ARTICLE", "PUBLISHED", NOW.minusSeconds(1));
        article("search-visible-note", "target", "visible", "NOTE", "PUBLISHED", NOW.minusSeconds(2));
        article("search-draft", "target", "hidden", "ARTICLE", "DRAFT", null);
        article("search-future", "target", "hidden", "ARTICLE", "PUBLISHED", NOW.plusSeconds(60));
        topic("search-visible-topic", "target", "visible", "PUBLISHED");
        topic("search-draft-topic", "target", "hidden", "DRAFT");
        tool("search-visible-tool", "target", "visible", "PUBLISHED", NOW.minusSeconds(3));
        tool("search-archived-tool", "target", "hidden", "ARCHIVED", NOW.minusSeconds(4));

        SearchRepository.SearchPage first = searchRepository.search("target", 0, 2, NOW);
        SearchRepository.SearchPage second = searchRepository.search("target", 1, 2, NOW);

        assertThat(first.total()).isEqualTo(4);
        assertThat(second.total()).isEqualTo(4);
        assertThat(first.items()).extracting(item -> item.type())
                .containsExactly(SearchResultType.ARTICLE, SearchResultType.NOTE);
        assertThat(second.items()).extracting(item -> item.type())
                .containsExactly(SearchResultType.TOOL, SearchResultType.TOPIC);
    }

    @Test
    void wildcardCharactersAreLiteralAndToolDescriptionsAreSearchable() {
        article("search-percent", "100%_match", "visible", "ARTICLE", "PUBLISHED", NOW.minusSeconds(1));
        article("search-decoy", "100xxmatch", "visible", "ARTICLE", "PUBLISHED", NOW.minusSeconds(2));
        tool("search-description-tool", "Utility", "contains needle only here", "PUBLISHED", NOW.minusSeconds(3));

        assertThat(searchRepository.search("%_", 0, 20, NOW).items())
                .extracting(item -> item.slug()).containsExactly("search-percent");
        assertThat(searchRepository.search("needle", 0, 20, NOW).items())
                .extracting(item -> item.slug()).containsExactly("search-description-tool");
    }

    @Test
    void sitemapUsesOnlyVisibleRowsFromAllPublicContentTypes() {
        article("map-visible-article", "Visible", "visible", "ARTICLE", "PUBLISHED", NOW.minusSeconds(1));
        article("map-visible-note", "Visible", "visible", "NOTE", "PUBLISHED", NOW.minusSeconds(2));
        article("map-draft", "Hidden", "hidden", "ARTICLE", "DRAFT", null);
        article("map-future", "Hidden", "hidden", "ARTICLE", "PUBLISHED", Instant.now().plusSeconds(3_600));
        topic("map-visible-topic", "Visible", "visible", "PUBLISHED");
        topic("map-draft-topic", "Hidden", "hidden", "DRAFT");
        tool("map-visible-tool", "Visible", "visible", "PUBLISHED", NOW.minusSeconds(3));
        tool("map-archived-tool", "Hidden", "hidden", "ARCHIVED", NOW.minusSeconds(4));

        String xml = sitemapService.generate();

        assertThat(xml).contains("/articles/map-visible-article", "/articles/map-visible-note",
                "/topics/map-visible-topic", "/tools/map-visible-tool");
        assertThat(xml).doesNotContain("map-draft", "map-future", "map-draft-topic", "map-archived-tool");
    }

    private void article(String slug, String title, String summary, String type, String status, Instant publishedAt) {
        jdbc.update("INSERT INTO article (slug, title, summary, markdown_content, content_type, status, published_at) "
                        + "VALUES (?, ?, ?, 'body', ?, ?, ?)",
                slug, title, summary, type, status, timestamp(publishedAt));
    }

    private void topic(String slug, String name, String description, String status) {
        jdbc.update("INSERT INTO topic (slug, name, normalized_name, description, status, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, 0)",
                slug, name, slug, description, status);
    }

    private void tool(String slug, String name, String description, String status, Instant publishedAt) {
        jdbc.update("INSERT INTO tool (slug, name, summary, description_markdown, status, featured, sort_order, published_at) "
                        + "VALUES (?, ?, 'public summary', ?, ?, 0, 0, ?)",
                slug, name, description, status, timestamp(publishedAt));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
