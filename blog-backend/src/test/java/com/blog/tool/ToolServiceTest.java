package com.blog.tool;

import com.blog.article.MarkdownRenderer;
import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.media.MediaPurpose;
import com.blog.media.MediaStatus;
import com.blog.media.ToolMediaReferenceService;
import com.blog.shared.error.ConflictException;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.CategoryScope;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.taxonomy.Tag;
import com.blog.taxonomy.TaxonomyService;
import com.blog.tool.dto.ToolWriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");

    @Mock ToolRepository toolRepository;
    @Mock MarkdownRenderer markdownRenderer;
    @Mock TaxonomyService taxonomyService;
    @Mock MediaAssetRepository mediaAssetRepository;
    @Mock SlugAllocationLockRepository slugAllocationLockRepository;
    @Mock ToolMediaReferenceService toolMediaReferenceService;
    private ToolService toolService;

    @BeforeEach
    void setUp() {
        toolService = new ToolService(toolRepository, markdownRenderer, taxonomyService, mediaAssetRepository,
                slugAllocationLockRepository, toolMediaReferenceService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createDraftNormalizesFieldsRendersWithSharedRendererAndUsesToolCategory() {
        ToolWriteRequest request = request("  \uff34\uff4f\uff4f\uff4c  ", null, " Summary ", "# Safe", "https://example.com/path", 4L, 7L,
                Set.of(11L), true, 9);
        Category category = category(7L);
        Tag tag = tag(11L);
        MediaAsset cover = image(4L);
        when(taxonomyService.requireCategory(7L, CategoryScope.TOOL)).thenReturn(category);
        when(taxonomyService.requireTags(Set.of(11L))).thenReturn(Set.of(tag));
        when(mediaAssetRepository.findById(4L)).thenReturn(Optional.of(cover));
        when(markdownRenderer.render("# Safe")).thenReturn("<h1 id=\"safe\">Safe</h1>");
        when(toolRepository.findMaxSortOrder()).thenReturn(-1);
        when(toolRepository.existsBySlug("tool")).thenReturn(false);
        when(toolRepository.save(any(Tool.class))).thenAnswer(invocation -> {
            Tool saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        toolService.createDraft(request);

        verify(slugAllocationLockRepository).lockSingleton();
        ArgumentCaptor<Tool> saved = ArgumentCaptor.forClass(Tool.class);
        verify(toolRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Tool");
        assertThat(saved.getValue().getSlug()).isEqualTo("tool");
        assertThat(saved.getValue().getOfficialUrl()).isEqualTo("https://example.com/path");
        assertThat(saved.getValue().getRenderedHtml()).isEqualTo("<h1 id=\"safe\">Safe</h1>");
        assertThat(saved.getValue().getCategory()).isSameAs(category);
        assertThat(saved.getValue().getTags()).containsExactly(tag);
        assertThat(saved.getValue().getCoverMedia()).isSameAs(cover);
        assertThat(saved.getValue().getStatus()).isEqualTo(ToolStatus.DRAFT);
        verify(toolMediaReferenceService).synchronize(saved.getValue(), "# Safe");
    }

    @Test
    void descriptionMarkdownUsesTheSharedSanitizingRenderer() {
        ToolService realRendererService = new ToolService(toolRepository, new MarkdownRenderer(), taxonomyService,
                mediaAssetRepository, slugAllocationLockRepository, toolMediaReferenceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(toolRepository.existsBySlug("tool")).thenReturn(false);
        when(toolRepository.findMaxSortOrder()).thenReturn(-1);
        when(toolRepository.save(any(Tool.class))).thenAnswer(invocation -> invocation.getArgument(0));

        realRendererService.createDraft(request("Tool", null, "Summary", "<script>alert(1)</script>\n\n[bad](javascript:x)",
                "https://example.com", null, null, Set.of(), false, 0));

        ArgumentCaptor<Tool> saved = ArgumentCaptor.forClass(Tool.class);
        verify(toolRepository).save(saved.capture());
        assertThat(saved.getValue().getRenderedHtml()).doesNotContain("<script", "javascript:");
    }

    @Test
    void updateWithUnchangedMarkdownPreservesTrustedRenderedHtml() {
        Tool tool = tool(2L, ToolStatus.DRAFT, "same");
        tool.setRenderedHtml("<p>trusted</p>");
        when(toolRepository.findById(2L)).thenReturn(Optional.of(tool));
        when(toolRepository.save(tool)).thenReturn(tool);

        toolService.update(2L, request("Changed", null, "Summary", "same", "https://example.com", null, null,
                Set.of(), false, 0));

        verify(markdownRenderer, never()).render(any());
        assertThat(tool.getRenderedHtml()).isEqualTo("<p>trusted</p>");
        verify(toolMediaReferenceService).synchronize(tool, "same");
    }

    @Test
    void rejectsUnsafeOrNonAbsoluteOfficialUrlsBeforeSaving() {
        for (String url : List.of("http://example.com", "javascript:alert(1)", "/tools", "https://user@example.com",
                "https://exa\u0000mple.com", "https:///missing-host")) {
            assertThatIllegalArgumentException().isThrownBy(() -> toolService.createDraft(
                    request("Tool", null, "Summary", "body", url, null, null, Set.of(), false, 0)));
        }
        verify(toolRepository, never()).save(any());
    }

    @Test
    void rejectsPostNormalizationExpansionAndSlugCollision() {
        assertThatIllegalArgumentException().isThrownBy(() -> toolService.createDraft(
                request("\ufdfa".repeat(12), null, "Summary", "body", "https://example.com", null, null,
                        Set.of(), false, 0)));
        when(toolRepository.existsBySlug("taken")).thenReturn(true);
        assertThatThrownBy(() -> toolService.createDraft(request("Tool", "taken", "Summary", "body",
                "https://example.com", null, null, Set.of(), false, 0))).isInstanceOf(ConflictException.class);
    }

    @Test
    void createAppendsAfterTheGlobalMaximumAndDoesNotAcceptCallerChosenOrder() {
        when(toolRepository.existsBySlug("tool")).thenReturn(false);
        when(toolRepository.findMaxSortOrder()).thenReturn(41);
        when(toolRepository.save(any(Tool.class))).thenAnswer(invocation -> invocation.getArgument(0));

        toolService.createDraft(request("Tool", null, "Summary", "body", "https://example.com", null, null,
                Set.of(), false, -999));

        ArgumentCaptor<Tool> saved = ArgumentCaptor.forClass(Tool.class);
        verify(toolRepository).save(saved.capture());
        assertThat(saved.getValue().getSortOrder()).isEqualTo(42);
    }

    @Test
    void validatesToolAssociationsBeforeSaving() {
        MediaAsset nonImage = image(4L);
        nonImage.setContentType("application/pdf");
        when(mediaAssetRepository.findById(4L)).thenReturn(Optional.of(nonImage));

        assertThatIllegalArgumentException().isThrownBy(() -> toolService.createDraft(request("Tool", null, "Summary",
                "body", "https://example.com", 4L, null, Set.of(), false, 0)));
        verify(toolRepository, never()).save(any());
    }

    @Test
    void newToolCoverMustBeReadyAndHaveToolCoverPurpose() {
        MediaAsset media = image(4L);
        media.setPurpose(MediaPurpose.ARTICLE_COVER);
        when(mediaAssetRepository.findById(4L)).thenReturn(Optional.of(media));

        assertThatIllegalArgumentException().isThrownBy(() -> toolService.createDraft(request("Tool", null, "Summary",
                "body", "https://example.com", 4L, null, Set.of(), false, 0)))
                .withMessageContaining("TOOL_COVER");
    }

    @Test
    void publicQueriesUseDatabasePaginationFiltersAndRequiredOrdering() {
        Tool first = tool(1L, ToolStatus.PUBLISHED, "body");
        first.setFeatured(true);
        first.setSortOrder(9);
        first.setPublishedAt(NOW.minusSeconds(5));
        when(toolRepository.findPublicPage(eq("java"), eq("spring"), eq("search"), eq(NOW), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first)));

        var result = toolService.listPublic(0, 20, "java", "spring", "search");

        assertThat(result.items()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(toolRepository).findPublicPage(eq("java"), eq("spring"), eq("search"), eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getSort().toList()).extracting(order -> order.getProperty() + ":" + order.getDirection())
                .containsExactly("featured:DESC", "sortOrder:ASC", "publishedAt:DESC", "id:DESC");
    }

    @Test
    void onlyPublishedNonFutureToolsArePublicAndArchivedToolsCannotBeEdited() {
        Tool visible = tool(1L, ToolStatus.PUBLISHED, "body");
        visible.setPublishedAt(NOW.minusSeconds(1));
        Tool draft = tool(2L, ToolStatus.DRAFT, "body");
        Tool archived = tool(3L, ToolStatus.ARCHIVED, "body");
        Tool future = tool(4L, ToolStatus.PUBLISHED, "body");
        future.setPublishedAt(NOW.plusSeconds(1));
        assertThat(visible.isVisibleAt(NOW)).isTrue();
        assertThat(draft.isVisibleAt(NOW)).isFalse();
        assertThat(archived.isVisibleAt(NOW)).isFalse();
        assertThat(future.isVisibleAt(NOW)).isFalse();

        when(toolRepository.findById(3L)).thenReturn(Optional.of(archived));
        assertThatThrownBy(() -> toolService.update(3L, request("Tool", null, "Summary", "body", "https://example.com",
                null, null, Set.of(), false, 0))).isInstanceOf(ConflictException.class);
    }

    @Test
    void publishedToolsCanArchiveAndPublicLookupUsesRepositoryVisibilityGate() {
        Tool tool = tool(2L, ToolStatus.DRAFT, "body");
        when(toolRepository.findById(2L)).thenReturn(Optional.of(tool));
        when(toolRepository.save(tool)).thenReturn(tool);

        toolService.publish(2L);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.PUBLISHED);
        assertThat(tool.getPublishedAt()).isEqualTo(NOW);
        toolService.archive(2L);
        assertThat(tool.getStatus()).isEqualTo(ToolStatus.ARCHIVED);

        when(toolRepository.findPublishedBySlug("hidden", NOW)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> toolService.findPublishedBySlug("hidden")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void reorderRequiresTheExactAllToolIdSetAndWritesContiguousPositionsOnlyAfterValidation() {
        Tool first = tool(1L, ToolStatus.PUBLISHED, "body");
        Tool second = tool(2L, ToolStatus.ARCHIVED, "body");
        when(toolRepository.findAllForReorder()).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> toolService.reorder(List.of(2L, 2L))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> toolService.reorder(List.of(1L))).isInstanceOf(ConflictException.class);
        verify(toolRepository, never()).saveAll(any());

        toolService.reorder(List.of(2L, 1L));
        assertThat(second.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);
        verify(toolRepository).saveAll(List.of(second, first));
    }

    @Test
    void publishingAfterACompleteReorderRetainsTheGlobalPosition() {
        Tool draft = tool(2L, ToolStatus.DRAFT, "body");
        draft.setSortOrder(7);
        when(toolRepository.findById(2L)).thenReturn(Optional.of(draft));
        when(toolRepository.save(draft)).thenReturn(draft);

        toolService.publish(2L);

        assertThat(draft.getStatus()).isEqualTo(ToolStatus.PUBLISHED);
        assertThat(draft.getSortOrder()).isEqualTo(7);
        verify(slugAllocationLockRepository).lockSingleton();
    }

    @Test
    void deleteCompactsEveryRemainingStatusUnderTheSharedMutex() {
        Tool first = tool(1L, ToolStatus.PUBLISHED, "body");
        Tool deleted = tool(2L, ToolStatus.ARCHIVED, "body");
        Tool last = tool(3L, ToolStatus.DRAFT, "body");
        first.setSortOrder(4); deleted.setSortOrder(7); last.setSortOrder(9);
        when(toolRepository.findById(2L)).thenReturn(Optional.of(deleted));
        when(toolRepository.findAllForReorder()).thenReturn(List.of(first, deleted, last));

        toolService.delete(2L);

        var deletion = inOrder(toolMediaReferenceService, toolRepository);
        deletion.verify(toolMediaReferenceService).removeAll(deleted);
        deletion.verify(toolRepository).delete(deleted);
        assertThat(first.getSortOrder()).isZero();
        assertThat(last.getSortOrder()).isEqualTo(1);
        verify(toolRepository).saveAll(List.of(first, last));
    }

    private static ToolWriteRequest request(String name, String slug, String summary, String markdown, String officialUrl,
                                            Long coverMediaId, Long categoryId, Set<Long> tagIds, boolean featured,
                                            int sortOrder) {
        return new ToolWriteRequest(name, slug, summary, markdown, officialUrl, coverMediaId, categoryId, tagIds,
                featured);
    }

    private static Tool tool(long id, ToolStatus status, String markdown) {
        Tool tool = new Tool();
        tool.setId(id);
        tool.setName("Tool " + id);
        tool.setSlug("tool-" + id);
        tool.setSummary("Summary");
        tool.setDescriptionMarkdown(markdown);
        tool.setOfficialUrl("https://example.com");
        tool.setStatus(status);
        return tool;
    }

    private static Category category(long id) {
        Category category = new Category();
        category.setId(id);
        category.setName("Tools");
        category.setSlug("tools");
        category.setScope(CategoryScope.TOOL);
        return category;
    }

    private static Tag tag(long id) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName("Spring");
        tag.setSlug("spring");
        return tag;
    }

    private static MediaAsset image(long id) {
        MediaAsset media = new MediaAsset();
        media.setId(id);
        media.setStorageKey("tool.png");
        media.setContentType("image/png");
        media.setStatus(MediaStatus.READY);
        media.setPurpose(MediaPurpose.TOOL_COVER);
        return media;
    }
}
