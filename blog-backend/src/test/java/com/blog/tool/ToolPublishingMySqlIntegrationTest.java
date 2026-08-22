package com.blog.tool;

import com.blog.taxonomy.Category;
import com.blog.taxonomy.CategoryRepository;
import com.blog.taxonomy.CategoryScope;
import com.blog.taxonomy.Tag;
import com.blog.taxonomy.TagRepository;
import com.blog.taxonomy.SlugAllocationLock;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.article.MarkdownRenderer;
import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import com.blog.taxonomy.TaxonomyService;
import com.blog.tool.dto.ToolWriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ToolPublishingMySqlIntegrationTest.ToolTestApplication.class, properties = {"spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=none", "spring.task.scheduling.enabled=false"})
@ActiveProfiles("test")
class ToolPublishingMySqlIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = JpaRepositoriesAutoConfiguration.class)
    @EntityScan(basePackageClasses = {Tool.class, Category.class, Tag.class, MediaAsset.class, SlugAllocationLock.class})
    @EnableJpaRepositories(basePackageClasses = {ToolRepository.class, CategoryRepository.class, TagRepository.class,
            MediaAssetRepository.class, SlugAllocationLockRepository.class})
    @EnableTransactionManagement
    @EnableJpaAuditing
    @Import({ToolService.class, TaxonomyService.class, MarkdownRenderer.class})
    static class ToolTestApplication {
    }
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
            } catch (ConflictException exception) {
                return exception.getClass().getSimpleName();
            }
        };
        try {
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            long deadline = tenSecondsFromNow();

            List<String> outcomes = List.of(await(first, deadline), await(second, deadline));
            assertThat(outcomes).contains("same-slug", "ConflictException");
            assertThat(assertCompleteContiguousOrder()).extracting(Tool::getSortOrder).containsExactly(0);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void twoDistinctConcurrentCreatesAppendDistinctContiguousPositions() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(createAfter(start, "One", "one"));
            var second = executor.submit(createAfter(start, "Two", "two"));
            start.countDown();
            long deadline = tenSecondsFromNow();
            List<Long> createdIds = List.of(await(first, deadline), await(second, deadline));
            assertThat(createdIds).doesNotHaveDuplicates();
            assertThat(assertCompleteContiguousOrder()).extracting(Tool::getId)
                    .containsExactlyInAnyOrderElementsOf(createdIds);
        } finally {
            shutdown(executor);
        }
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
        try {
            var reorder = executor.submit(reorderAfter(start, List.of(draftId, publishedId)));
            var publish = executor.submit(publishAfter(start, draftId));
            start.countDown();
            long deadline = tenSecondsFromNow();
            assertThat(await(reorder, deadline)).isEqualTo(MutationOutcome.SUCCEEDED);
            assertThat(await(publish, deadline)).isEqualTo(MutationOutcome.SUCCEEDED);
        } finally {
            shutdown(executor);
        }

        assertThat(assertCompleteContiguousOrder()).extracting(Tool::getId).containsExactly(draftId, publishedId);
        assertThat(toolRepository.findById(draftId).orElseThrow().getStatus()).isEqualTo(ToolStatus.PUBLISHED);
        toolService.archive(draftId);
        assertThat(toolRepository.findById(draftId).orElseThrow().getSortOrder()).isZero();
        assertCompleteContiguousOrder();
    }

    @Test
    void createAndCompleteReorderSerializeWithoutLosingOrDuplicatingRows() throws Exception {
        List<Long> initialIds = createDrafts("create-race", 3);
        List<Long> submittedOrder = List.of(initialIds.get(2), initialIds.get(0), initialIds.get(1));
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        long createdId;
        MutationOutcome reorderOutcome;
        try {
            var create = executor.submit(createAfter(start, "Concurrent Create", "concurrent-create"));
            var reorder = executor.submit(reorderAfter(start, submittedOrder));
            start.countDown();
            long deadline = tenSecondsFromNow();
            createdId = await(create, deadline);
            reorderOutcome = await(reorder, deadline);
        } finally {
            shutdown(executor);
        }

        assertThat(reorderOutcome).isIn(MutationOutcome.SUCCEEDED, MutationOutcome.CONFLICT);
        List<Long> expectedIds = List.of(initialIds.get(0), initialIds.get(1), initialIds.get(2), createdId);
        assertThat(assertCompleteContiguousOrder()).extracting(Tool::getId)
                .containsExactlyInAnyOrderElementsOf(expectedIds);
        assertThat(toolRepository.findById(createdId)).isPresent();
    }

    @Test
    void deleteAndCompleteReorderSerializeWithoutRetainingOrDuplicatingTheTarget() throws Exception {
        List<Long> initialIds = createDrafts("delete-race", 4);
        long deletedId = initialIds.get(1);
        List<Long> submittedOrder = List.of(initialIds.get(3), deletedId, initialIds.get(0), initialIds.get(2));
        List<Long> survivorIds = List.of(initialIds.get(0), initialIds.get(2), initialIds.get(3));
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        MutationOutcome reorderOutcome;
        try {
            var delete = executor.submit(deleteAfter(start, deletedId));
            var reorder = executor.submit(reorderAfter(start, submittedOrder));
            start.countDown();
            long deadline = tenSecondsFromNow();
            assertThat(await(delete, deadline)).isEqualTo(MutationOutcome.SUCCEEDED);
            reorderOutcome = await(reorder, deadline);
        } finally {
            shutdown(executor);
        }

        assertThat(reorderOutcome).isIn(MutationOutcome.SUCCEEDED, MutationOutcome.CONFLICT,
                MutationOutcome.NOT_FOUND);
        assertThat(toolRepository.findById(deletedId)).isEmpty();
        assertThat(assertCompleteContiguousOrder()).extracting(Tool::getId)
                .containsExactlyInAnyOrderElementsOf(survivorIds);
    }

    @Test
    void competingCompleteReordersSerializeAndLeaveOneSubmittedPermutation() throws Exception {
        List<Long> ids = createDrafts("reorder-race", 4);
        List<Long> firstPermutation = List.of(ids.get(3), ids.get(1), ids.get(0), ids.get(2));
        List<Long> secondPermutation = List.of(ids.get(2), ids.get(0), ids.get(3), ids.get(1));
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(reorderAfter(start, firstPermutation));
            var second = executor.submit(reorderAfter(start, secondPermutation));
            start.countDown();
            long deadline = tenSecondsFromNow();
            assertThat(await(first, deadline)).isEqualTo(MutationOutcome.SUCCEEDED);
            assertThat(await(second, deadline)).isEqualTo(MutationOutcome.SUCCEEDED);
        } finally {
            shutdown(executor);
        }

        List<Long> finalOrder = assertCompleteContiguousOrder().stream().map(Tool::getId).toList();
        assertThat(finalOrder).isIn(firstPermutation, secondPermutation);
    }

    private Callable<Long> createAfter(CountDownLatch start, String name, String slug) {
        return () -> {
            start.await();
            return toolService.createDraft(request(name, slug)).id();
        };
    }

    private Callable<MutationOutcome> reorderAfter(CountDownLatch start, List<Long> orderedIds) {
        return () -> {
            start.await();
            try {
                toolService.reorder(orderedIds);
                return MutationOutcome.SUCCEEDED;
            } catch (ConflictException exception) {
                return MutationOutcome.CONFLICT;
            } catch (ResourceNotFoundException exception) {
                return MutationOutcome.NOT_FOUND;
            }
        };
    }

    private Callable<MutationOutcome> deleteAfter(CountDownLatch start, long id) {
        return () -> {
            start.await();
            toolService.delete(id);
            return MutationOutcome.SUCCEEDED;
        };
    }

    private Callable<MutationOutcome> publishAfter(CountDownLatch start, long id) {
        return () -> {
            start.await();
            toolService.publish(id);
            return MutationOutcome.SUCCEEDED;
        };
    }

    private List<Long> createDrafts(String slugPrefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> toolService.createDraft(request("Tool " + index, slugPrefix + "-" + index)).id())
                .toList();
    }

    private List<Tool> assertCompleteContiguousOrder() {
        List<Tool> ordered = toolRepository.findAll(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
        List<Long> orderedIds = ordered.stream().map(Tool::getId).toList();
        Set<Long> actualDatabaseIds = toolRepository.findAll().stream().map(Tool::getId)
                .collect(Collectors.toSet());

        assertThat(ordered).extracting(Tool::getSortOrder)
                .containsExactlyElementsOf(IntStream.range(0, ordered.size()).boxed().toList());
        assertThat(orderedIds).doesNotHaveDuplicates();
        assertThat(new HashSet<>(orderedIds)).isEqualTo(actualDatabaseIds);
        return ordered;
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).as("concurrent test workers terminated").isTrue();
    }

    private static long tenSecondsFromNow() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    }

    private static <T> T await(Future<T> future, long deadline) throws Exception {
        long remainingNanos = Math.max(1, deadline - System.nanoTime());
        return future.get(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private enum MutationOutcome {
        SUCCEEDED,
        CONFLICT,
        NOT_FOUND
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
