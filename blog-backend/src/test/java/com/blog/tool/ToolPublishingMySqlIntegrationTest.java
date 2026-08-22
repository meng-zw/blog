package com.blog.tool;

import com.blog.taxonomy.Category;
import com.blog.taxonomy.CategoryRepository;
import com.blog.taxonomy.CategoryScope;
import com.blog.taxonomy.Tag;
import com.blog.taxonomy.TagRepository;
import com.blog.tool.dto.ToolWriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=none", "blog.admin.bootstrap.username=", "spring.task.scheduling.enabled=false"})
@ActiveProfiles("test")
class ToolPublishingMySqlIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired ToolRepository toolRepository;
    @Autowired ToolService toolService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TagRepository tagRepository;

    private Category java;
    private Tag spring;

    @BeforeEach
    void cleanAndSeedTaxonomy() {
        toolRepository.deleteAll();
        tagRepository.deleteAll();
        categoryRepository.deleteAll();
        java = category("Java", "java");
        spring = tag("Spring", "spring");
    }

    @Test
    void realRepositoryFiltersDistinctCountsAndOrdersOnlyVisibleRows() {
        Tool first = visible("Featured", "featured", 9, true, Instant.parse("2026-08-20T00:00:00Z"));
        first.setCategory(java);
        first.setTags(Set.of(spring, tag("JPA", "jpa")));
        toolRepository.save(first);
        Tool second = visible("Spring Search", "search", 1, false, Instant.parse("2026-08-21T00:00:00Z"));
        second.setCategory(java);
        second.setTags(Set.of(spring));
        toolRepository.save(second);
        Tool draftFixture = visible("Draft", "draft", 0, true, Instant.parse("2026-08-22T00:00:00Z"));
        draftFixture.setStatus(ToolStatus.DRAFT);
        toolRepository.save(draftFixture);
        Tool future = visible("Future", "future", 0, true, Instant.now().plusSeconds(3600));
        toolRepository.save(future);

        var page = toolService.listPublic(0, 20, "java", "spring", "search");
        var all = toolService.listPublic(0, 20, null, null, null);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(item -> item.slug()).containsExactly("search");
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items()).extracting(item -> item.slug()).containsExactly("featured", "search");
    }

    @Test
    void concurrentSlugCollisionAndAppendsAreSerializedByTheDurableMutex() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> create = () -> {
            start.await();
            try {
                return toolService.createDraft(request("Same", "same-slug")).slug();
            } catch (RuntimeException exception) {
                return exception.getClass().getSimpleName();
            }
        };
        var first = executor.submit(create);
        var second = executor.submit(create);
        start.countDown();

        List<String> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertThat(outcomes).contains("same-slug", "ConflictException");
        assertThat(toolRepository.findAll()).extracting(Tool::getSortOrder).containsExactly(0);
    }

    @Test
    void twoDistinctConcurrentCreatesAppendDistinctContiguousPositions() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var first = executor.submit(() -> { start.await(); return toolService.createDraft(request("One", "one")).id(); });
        var second = executor.submit(() -> { start.await(); return toolService.createDraft(request("Two", "two")).id(); });
        start.countDown();
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).doesNotHaveDuplicates();
        executor.shutdown();
        assertThat(toolRepository.findAll().stream().map(Tool::getSortOrder).sorted().toList()).containsExactly(0, 1);
    }

    @Test
    void reorderAndPublishSerializeAndRetainACompleteContiguousGlobalOrder() throws Exception {
        Tool published = visible("Published", "published", 0, false, Instant.parse("2026-08-20T00:00:00Z"));
        Tool draft = visible("Draft", "draft", 1, false, Instant.parse("2026-08-20T00:00:00Z"));
        draft.setStatus(ToolStatus.DRAFT);
        published = toolRepository.save(published);
        draft = toolRepository.save(draft);
        long publishedId = published.getId();
        long draftId = draft.getId();

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var reorder = executor.submit(() -> { start.await(); toolService.reorder(List.of(draftId, publishedId)); return true; });
        var publish = executor.submit(() -> { start.await(); toolService.publish(draftId); return true; });
        start.countDown();
        assertThat(reorder.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(publish.get(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(toolRepository.findAll().stream().sorted(Comparator.comparingInt(Tool::getSortOrder)).toList())
                .extracting(Tool::getId).containsExactly(draftId, publishedId);
        assertThat(toolRepository.findAll().stream().sorted(Comparator.comparingInt(Tool::getSortOrder)).toList())
                .extracting(Tool::getSortOrder).containsExactly(0, 1);
        assertThat(toolRepository.findById(draftId).orElseThrow().getStatus()).isEqualTo(ToolStatus.PUBLISHED);
        toolService.archive(draftId);
        assertThat(toolRepository.findById(draftId).orElseThrow().getSortOrder()).isZero();
    }

    private Category category(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setNormalizedName(name.toLowerCase());
        category.setSlug(slug);
        category.setScope(CategoryScope.TOOL);
        return categoryRepository.save(category);
    }

    private Tag tag(String name, String slug) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setNormalizedName(name.toLowerCase());
        tag.setSlug(slug);
        return tagRepository.save(tag);
    }

    private static Tool visible(String name, String slug, int order, boolean featured, Instant publishedAt) {
        Tool tool = new Tool();
        tool.setName(name);
        tool.setSlug(slug);
        tool.setSummary(name + " summary");
        tool.setDescriptionMarkdown("body");
        tool.setRenderedHtml("<p>body</p>");
        tool.setOfficialUrl("https://example.com/" + slug);
        tool.setStatus(ToolStatus.PUBLISHED);
        tool.setFeatured(featured);
        tool.setSortOrder(order);
        tool.setPublishedAt(publishedAt);
        return tool;
    }

    private static ToolWriteRequest request(String name, String slug) {
        return new ToolWriteRequest(name, slug, "Summary", "body", "https://example.com", null, null, Set.of(), false);
    }
}
