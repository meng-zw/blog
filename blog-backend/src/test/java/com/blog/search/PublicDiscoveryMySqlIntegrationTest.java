package com.blog.search;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.search.dto.SearchResultType;
import com.blog.site.HomeQueryService;
import com.blog.site.SiteProfile;
import com.blog.site.SiteProfileRepository;
import com.blog.site.SiteProfileService;
import com.blog.site.SitemapService;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.SlugAllocationLock;
import com.blog.taxonomy.Tag;
import com.blog.tool.Tool;
import com.blog.tool.ToolRepository;
import com.blog.topic.Topic;
import com.blog.topic.TopicArticle;
import com.blog.topic.TopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PublicDiscoveryMySqlIntegrationTest.PublicDiscoveryTestApplication.class,
        properties = {"spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=none",
                "spring.task.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PublicDiscoveryMySqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = JpaRepositoriesAutoConfiguration.class)
    @EntityScan(basePackageClasses = {Article.class, Tool.class, Topic.class, TopicArticle.class, Category.class,
            Tag.class, SiteProfile.class, MediaAsset.class, SlugAllocationLock.class})
    @EnableJpaRepositories(basePackageClasses = {ArticleRepository.class, ToolRepository.class, TopicRepository.class,
            SiteProfileRepository.class, MediaAssetRepository.class})
    @EnableTransactionManagement
    @EnableJpaAuditing
    @Import({SearchRepository.class, SearchService.class, SitemapService.class, HomeQueryService.class,
            SiteProfileService.class})
    static class PublicDiscoveryTestApplication {
    }

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
    @Autowired HomeQueryService homeQueryService;

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
        toolWith("search-summary-tool", "Plain utility", "summary-token only here", "description absent",
                "PUBLISHED", false, 0, NOW.minusSeconds(4));

        assertThat(searchRepository.search("%_", 0, 20, NOW).items())
                .extracting(item -> item.slug()).containsExactly("search-percent");
        assertThat(searchRepository.search("needle", 0, 20, NOW).items())
                .extracting(item -> item.slug()).containsExactly("search-description-tool");
        assertThat(searchRepository.search("summary-token", 0, 20, NOW).items())
                .extracting(item -> item.slug()).containsExactly("search-summary-tool");
    }

    @Test
    void homeUsesRealBoundedVisibleArticleOnlyQueriesAndEstablishedToolTopicOrder() {
        Instant now = Instant.now();
        article("home-note-newest", "Newest note", "note", "NOTE", "PUBLISHED", now.minusSeconds(5));
        article("home-featured", "Featured", "featured", "ARTICLE", "PUBLISHED", now.minusSeconds(10));
        article("home-latest-1", "Latest 1", "latest", "ARTICLE", "PUBLISHED", now.minusSeconds(20));
        article("home-latest-2", "Latest 2", "latest", "ARTICLE", "PUBLISHED", now.minusSeconds(30));
        article("home-latest-3", "Latest 3", "latest", "ARTICLE", "PUBLISHED", now.minusSeconds(40));
        article("home-latest-4", "Latest 4", "latest", "ARTICLE", "PUBLISHED", now.minusSeconds(50));
        article("home-older", "Older", "older", "ARTICLE", "PUBLISHED", now.minusSeconds(60));
        article("home-draft", "Draft", "hidden", "ARTICLE", "DRAFT", null);
        article("home-future", "Future", "hidden", "ARTICLE", "PUBLISHED", now.plusSeconds(3_600));
        article("home-archived", "Archived", "hidden", "ARTICLE", "ARCHIVED", now.minusSeconds(1));
        toolWith("home-tool-second", "Second", "public", "description", "PUBLISHED", true, 2,
                now.minusSeconds(10));
        toolWith("home-tool-first", "First", "public", "description", "PUBLISHED", true, 1,
                now.minusSeconds(20));
        toolWith("home-tool-third", "Third", "public", "description", "PUBLISHED", true, 3,
                now.minusSeconds(30));
        toolWith("home-tool-fourth", "Fourth", "public", "description", "PUBLISHED", true, 4,
                now.minusSeconds(40));
        toolWith("home-tool-fifth", "Fifth", "bounded", "description", "PUBLISHED", true, 5,
                now.minusSeconds(50));
        toolWith("home-tool-not-featured", "Not featured", "hidden", "description", "PUBLISHED", false, 0,
                now.minusSeconds(1));
        toolWith("home-tool-draft", "Draft", "hidden", "description", "DRAFT", true, 0, null);
        toolWith("home-tool-future", "Future", "hidden", "description", "PUBLISHED", true, 0,
                now.plusSeconds(3_600));
        toolWith("home-tool-archived", "Archived", "hidden", "description", "ARCHIVED", true, 0,
                now.minusSeconds(1));
        topic("home-topic-2", "Topic 2", "visible", "PUBLISHED", 2);
        topic("home-topic-1", "Topic 1", "visible", "PUBLISHED", 1);
        topic("home-topic-3", "Topic 3", "visible", "PUBLISHED", 3);
        topic("home-topic-4", "Topic 4", "visible", "PUBLISHED", 4);
        topic("home-topic-5", "Topic 5", "bounded", "PUBLISHED", 5);
        topic("home-topic-draft", "Draft topic", "hidden", "DRAFT", 0);

        var home = homeQueryService.getHome();

        assertThat(home.site().siteTitle()).isEqualTo("小M的思与行");
        assertThat(home.featuredArticle().slug()).isEqualTo("home-featured");
        assertThat(home.latestArticles()).hasSize(4)
                .allMatch(article -> article.contentType() == com.blog.article.ContentType.ARTICLE)
                .extracting(article -> article.slug())
                .containsExactly("home-latest-1", "home-latest-2", "home-latest-3", "home-latest-4");
        assertThat(home.featuredTools()).hasSize(4).extracting(tool -> tool.slug())
                .containsExactly("home-tool-first", "home-tool-second", "home-tool-third", "home-tool-fourth");
        assertThat(home.topics()).hasSize(4).extracting(topic -> topic.slug())
                .containsExactly("home-topic-1", "home-topic-2", "home-topic-3", "home-topic-4");
    }

    @Test
    void homeRetainsTheSeededSiteWhenOptionalSectionsAreEmpty() {
        var home = homeQueryService.getHome();

        assertThat(home.site().siteTitle()).isEqualTo("小M的思与行");
        assertThat(home.featuredArticle()).isNull();
        assertThat(home.latestArticles()).isEmpty();
        assertThat(home.featuredTools()).isEmpty();
        assertThat(home.topics()).isEmpty();
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
        topic(slug, name, description, status, 0);
    }

    private void topic(String slug, String name, String description, String status, int sortOrder) {
        jdbc.update("INSERT INTO topic (slug, name, normalized_name, description, status, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                slug, name, slug, description, status, sortOrder);
    }

    private void tool(String slug, String name, String description, String status, Instant publishedAt) {
        toolWith(slug, name, "public summary", description, status, false, 0, publishedAt);
    }

    private void toolWith(String slug, String name, String summary, String description, String status,
                          boolean featured, int sortOrder, Instant publishedAt) {
        jdbc.update("INSERT INTO tool (slug, name, summary, description_markdown, status, featured, sort_order, published_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                slug, name, summary, description, status, featured, sortOrder, timestamp(publishedAt));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
