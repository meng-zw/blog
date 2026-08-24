package com.blog.article;

import com.blog.article.dto.ArticleWriteRequest;
import com.blog.media.MediaAsset;
import com.blog.media.MediaAssetRepository;
import com.blog.media.ArticleMediaReferenceService;
import com.blog.media.ArticleMedia;
import com.blog.media.ArticleMediaRole;
import com.blog.media.MediaPurpose;
import com.blog.media.MediaStatus;
import com.blog.shared.error.ConflictException;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.CategoryScope;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.taxonomy.Tag;
import com.blog.taxonomy.TaxonomyService;
import com.blog.topic.Topic;
import com.blog.topic.TopicRepository;
import com.blog.topic.TopicStatus;
import com.blog.topic.TopicMembershipManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");

    @Mock ArticleRepository articleRepository;
    @Mock MarkdownRenderer markdownRenderer;
    @Mock TaxonomyService taxonomyService;
    @Mock MediaAssetRepository mediaAssetRepository;
    @Mock TopicRepository topicRepository;
    @Mock SlugAllocationLockRepository slugAllocationLockRepository;
    @Mock TopicMembershipManager topicMembershipManager;
    @Mock ArticleMediaReferenceService articleMediaReferenceService;
    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        articleService = new ArticleService(articleRepository, markdownRenderer, taxonomyService,
                mediaAssetRepository, topicRepository, slugAllocationLockRepository,
                topicMembershipManager, articleMediaReferenceService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createDraftValidatesAssociationsRendersMarkdownAndAllocatesSlugUnderTheSharedLock() {
        ArticleWriteRequest request = request("A Java Note", "# body", 4L, 7L, 9L, Set.of(11L));
        Category category = category(7L);
        Tag tag = tag(11L);
        Topic topic = topic(9L, TopicStatus.PUBLISHED);
        MediaAsset cover = image(4L);
        when(taxonomyService.requireCategory(7L, CategoryScope.ARTICLE)).thenReturn(category);
        when(taxonomyService.requireTags(Set.of(11L))).thenReturn(Set.of(tag));
        when(topicRepository.findById(9L)).thenReturn(Optional.of(topic));
        when(mediaAssetRepository.findById(4L)).thenReturn(Optional.of(cover));
        when(markdownRenderer.render("# body")).thenReturn("<h1 id=\"body\">body</h1>");
        when(articleRepository.existsBySlug("a-java-note")).thenReturn(false);
        when(articleRepository.save(any(Article.class))).thenAnswer(call -> {
            Article value = call.getArgument(0);
            value.setId(20L);
            return value;
        });

        articleService.createDraft(request);

        var ordered = org.mockito.Mockito.inOrder(slugAllocationLockRepository, articleRepository);
        ordered.verify(slugAllocationLockRepository).lockSingleton();
        ordered.verify(articleRepository).existsBySlug("a-java-note");
        ArgumentCaptor<Article> saved = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(saved.capture());
        verify(topicMembershipManager).synchronizeArticle(saved.getValue());
        verify(articleMediaReferenceService).synchronize(saved.getValue(), "# body", List.of());
        assertThat(saved.getValue().getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(saved.getValue().getRenderedHtml()).isEqualTo("<h1 id=\"body\">body</h1>");
        assertThat(saved.getValue().getCategory()).isSameAs(category);
        assertThat(saved.getValue().getTags()).containsExactly(tag);
        assertThat(saved.getValue().getTopic()).isSameAs(topic);
        assertThat(saved.getValue().getCoverMedia()).isSameAs(cover);
    }

    @Test
    void createDraftRejectsNonImageCoverBeforeSaving() {
        MediaAsset pdf = image(4L);
        pdf.setContentType("application/pdf");
        when(mediaAssetRepository.findById(4L)).thenReturn(Optional.of(pdf));

        assertThatIllegalArgumentException().isThrownBy(() -> articleService.createDraft(
                request("Title", "body", 4L, null, null, Set.of())));
        verify(articleRepository, never()).save(any());
    }

    @Test
    void newArticleCoverMustBeReadyAndHaveArticleCoverPurposeWhileExistingLegacyCoverRemainsValid() {
        MediaAsset wrongPurpose = image(4L);
        wrongPurpose.setPurpose(MediaPurpose.INLINE_IMAGE);
        wrongPurpose.setStatus(MediaStatus.READY);
        when(mediaAssetRepository.findById(4L)).thenReturn(Optional.of(wrongPurpose));

        assertThatIllegalArgumentException().isThrownBy(() -> articleService.createDraft(
                request("Title", "body", 4L, null, null, Set.of())))
                .withMessageContaining("ARTICLE_COVER");

        Article existing = article(2L, ArticleStatus.DRAFT, ContentType.ARTICLE);
        existing.setCoverMedia(wrongPurpose);
        when(articleRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(articleRepository.save(existing)).thenReturn(existing);
        articleService.update(2L, request("Changed", "body", 4L, null, null, Set.of()));
        assertThat(existing.getCoverMedia()).isSameAs(wrongPurpose);
    }

    @Test
    void articleResponsesUseStableMediaIdUrls() {
        Article article = article(2L, ArticleStatus.PUBLISHED, ContentType.ARTICLE);
        article.setCoverMedia(image(12L));
        assertThat(ArticleService.summary(article).coverUrl()).isEqualTo("/api/media/assets/12");
    }

    @Test
    void createDraftRejectsMissingCoverMediaBeforeSaving() {
        when(mediaAssetRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.createDraft(
                request("Title", "body", 404L, null, null, Set.of())))
                .isInstanceOf(com.blog.shared.error.ResourceNotFoundException.class);
        verify(articleRepository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("normalizedExpansionRequests")
    void compatibilityExpansionBeyondApprovedBoundsIsRejectedBeforeAnyRepositoryCall(ArticleWriteRequest request) {
        assertThatIllegalArgumentException().isThrownBy(() -> articleService.createDraft(request));

        verify(slugAllocationLockRepository, never()).lockSingleton();
        verify(articleRepository, never()).save(any());
    }

    @Test
    void updateDoesNotRenderWhenMarkdownHasNotChanged() {
        Article article = article(2L, ArticleStatus.DRAFT, ContentType.ARTICLE);
        article.setMarkdownContent("same markdown");
        article.setRenderedHtml("<p>trusted</p>");
        when(articleRepository.findById(2L)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        articleService.update(2L, request("Changed title", "same markdown", null, null, null, Set.of()));

        verify(markdownRenderer, never()).render(any());
        verify(topicMembershipManager).synchronizeArticle(article);
        assertThat(article.getRenderedHtml()).isEqualTo("<p>trusted</p>");
    }

    @Test
    void draftCanPublishImmediatelyAndPublishedContentCanArchive() {
        Article article = article(2L, ArticleStatus.DRAFT, ContentType.ARTICLE);
        when(articleRepository.findById(2L)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        articleService.publishNow(2L);
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(article.getPublishedAt()).isEqualTo(NOW);

        articleService.archive(2L);
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.ARCHIVED);
    }

    @Test
    void draftCanScheduleInFutureButNotInPast() {
        Article article = article(2L, ArticleStatus.DRAFT, ContentType.ARTICLE);
        when(articleRepository.findById(2L)).thenReturn(Optional.of(article));
        when(articleRepository.save(article)).thenReturn(article);

        articleService.schedule(2L, NOW.plusSeconds(60));
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.SCHEDULED);
        assertThat(article.getScheduledAt()).isEqualTo(NOW.plusSeconds(60));

        assertThatIllegalArgumentException().isThrownBy(() -> articleService.schedule(2L, NOW.minusSeconds(1)));
    }

    @Test
    void publishDueUsesOneBoundedConditionalUpdateAndIsIdempotent() {
        Article first = article(1L, ArticleStatus.SCHEDULED, ContentType.ARTICLE);
        first.setScheduledAt(NOW.minusSeconds(2));
        Article second = article(2L, ArticleStatus.SCHEDULED, ContentType.NOTE);
        second.setScheduledAt(NOW.minusSeconds(1));
        when(articleRepository.findDueForPublishing(eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(first, second), List.of());

        assertThat(articleService.publishDue(NOW)).isEqualTo(2);
        assertThat(articleService.publishDue(NOW)).isZero();
        assertThat(first.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(second.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
    }

    @Test
    void illegalStateTransitionIsAConflict() {
        Article archived = article(2L, ArticleStatus.ARCHIVED, ContentType.ARTICLE);
        when(articleRepository.findById(2L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> articleService.publishNow(2L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void publishingRejectsArticleAssignedToDraftTopic() {
        Article draft = article(2L, ArticleStatus.DRAFT, ContentType.ARTICLE);
        draft.setTopic(topic(9L, TopicStatus.DRAFT));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> articleService.publishNow(2L)).isInstanceOf(ConflictException.class);
        verify(articleRepository, never()).save(any());
    }

    @Test
    void schedulingRejectsArticleAssignedToDraftTopic() {
        Article draft = article(2L, ArticleStatus.DRAFT, ContentType.ARTICLE);
        draft.setTopic(topic(9L, TopicStatus.DRAFT));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> articleService.schedule(2L, NOW.plusSeconds(60)))
                .isInstanceOf(ConflictException.class);
        verify(articleRepository, never()).save(any());
    }

    @Test
    void duePublishingDefensivelySkipsArticleWhoseTopicBecameDraft() {
        Article scheduled = article(2L, ArticleStatus.SCHEDULED, ContentType.ARTICLE);
        scheduled.setScheduledAt(NOW.minusSeconds(1));
        scheduled.setTopic(topic(9L, TopicStatus.DRAFT));
        when(articleRepository.findDueForPublishing(eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(scheduled));

        assertThat(articleService.publishDue(NOW)).isZero();
        assertThat(scheduled.getStatus()).isEqualTo(ArticleStatus.SCHEDULED);
        verify(articleRepository, never()).saveAll(any());
    }

    @Test
    void publicDetailOmitsDraftTopicDefensively() {
        Article published = article(2L, ArticleStatus.PUBLISHED, ContentType.ARTICLE);
        published.setPublishedAt(NOW.minusSeconds(1));
        published.setTopic(topic(9L, TopicStatus.DRAFT));
        when(articleRepository.findPublishedBySlug("article-2", NOW)).thenReturn(Optional.of(published));
        when(articleRepository.findPreviousVisible(eq(ContentType.ARTICLE), any(), eq(2L), eq(NOW), any()))
                .thenReturn(List.of());
        when(articleRepository.findNextVisible(eq(ContentType.ARTICLE), any(), eq(2L), eq(NOW), any()))
                .thenReturn(List.of());

        assertThat(articleService.findPublishedBySlug("article-2").topic()).isNull();
    }

    @Test
    void publicVisibilityRequiresPublishedStateAndNonFuturePublishedTime() {
        Article visible = article(1L, ArticleStatus.PUBLISHED, ContentType.ARTICLE);
        visible.setPublishedAt(NOW.minusSeconds(1));
        Article draft = article(2L, ArticleStatus.DRAFT, ContentType.ARTICLE);
        Article archived = article(3L, ArticleStatus.ARCHIVED, ContentType.ARTICLE);
        archived.setPublishedAt(NOW.minusSeconds(1));
        Article future = article(4L, ArticleStatus.PUBLISHED, ContentType.ARTICLE);
        future.setPublishedAt(NOW.plusSeconds(1));

        assertThat(visible.isVisibleAt(NOW)).isTrue();
        assertThat(draft.isVisibleAt(NOW)).isFalse();
        assertThat(archived.isVisibleAt(NOW)).isFalse();
        assertThat(future.isVisibleAt(NOW)).isFalse();
    }

    @Test
    void scheduledArticleCanPublishImmediately() {
        Article scheduled = article(2L, ArticleStatus.SCHEDULED, ContentType.NOTE);
        scheduled.setScheduledAt(NOW.plusSeconds(60));
        when(articleRepository.findById(2L)).thenReturn(Optional.of(scheduled));
        when(articleRepository.save(scheduled)).thenReturn(scheduled);

        articleService.publishNow(2L);

        assertThat(scheduled.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
        assertThat(scheduled.getScheduledAt()).isNull();
        assertThat(scheduled.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void adminDetailRetainsMarkdownAndWorkflowStateForEditing() {
        Article draft = article(2L, ArticleStatus.DRAFT, ContentType.NOTE);
        draft.setMarkdownContent("# editable source");
        when(articleRepository.findById(2L)).thenReturn(Optional.of(draft));

        var response = articleService.findAdmin(2L);

        assertThat(response.markdownContent()).isEqualTo("# editable source");
        assertThat(response.status()).isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    void articleDetailsExposeOrderedPublicAttachmentMetadataWithStableDownloadUrls() {
        Article draft = article(2L, ArticleStatus.DRAFT, ContentType.NOTE);
        MediaAsset attachment = new MediaAsset();
        attachment.setId(31L);
        attachment.setOriginalFilename("guide.pdf");
        attachment.setContentType("application/pdf");
        attachment.setByteSize(1_024L);
        ArticleMedia reference = new ArticleMedia(draft, attachment, ArticleMediaRole.ATTACHMENT,
                "Download guide.pdf", 0, NOW);
        when(articleRepository.findById(2L)).thenReturn(Optional.of(draft));
        when(articleMediaReferenceService.attachmentsFor(2L)).thenReturn(List.of(reference));

        var response = articleService.findAdmin(2L);

        assertThat(response.attachments()).singleElement().satisfies(value -> {
            assertThat(value.mediaId()).isEqualTo(31L);
            assertThat(value.displayName()).isEqualTo("Download guide.pdf");
            assertThat(value.contentType()).isEqualTo("application/pdf");
            assertThat(value.byteSize()).isEqualTo(1_024L);
            assertThat(value.downloadUrl()).isEqualTo("/api/media/assets/31/download");
        });
    }

    @Test
    void publishedDetailLoadsOnlySameTypeVisibleNeighbors() {
        Article current = article(2L, ArticleStatus.PUBLISHED, ContentType.NOTE);
        current.setPublishedAt(NOW.minusSeconds(30));
        Article previous = article(1L, ArticleStatus.PUBLISHED, ContentType.NOTE);
        previous.setPublishedAt(NOW.minusSeconds(60));
        Article next = article(3L, ArticleStatus.PUBLISHED, ContentType.NOTE);
        next.setPublishedAt(NOW.minusSeconds(10));
        when(articleRepository.findPublishedBySlug("article-2", NOW)).thenReturn(Optional.of(current));
        when(articleRepository.findPreviousVisible(eq(ContentType.NOTE), eq(current.getPublishedAt()), eq(2L),
                eq(NOW), any(Pageable.class))).thenReturn(List.of(previous));
        when(articleRepository.findNextVisible(eq(ContentType.NOTE), eq(current.getPublishedAt()), eq(2L),
                eq(NOW), any(Pageable.class))).thenReturn(List.of(next));

        var response = articleService.findPublishedBySlug("article-2");

        assertThat(response.previous().id()).isEqualTo(1L);
        assertThat(response.next().id()).isEqualTo(3L);
    }

    @Test
    void publicListDelegatesPaginationToRepositoryWithDeterministicPublishedOrder() {
        Article visible = article(1L, ArticleStatus.PUBLISHED, ContentType.ARTICLE);
        visible.setPublishedAt(NOW.minusSeconds(1));
        when(articleRepository.findPublicPage(eq(ContentType.ARTICLE), eq("java"), eq("spring"),
                eq("series"), eq("query"), eq(NOW), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visible)));

        var result = articleService.listPublic(2, 10, ContentType.ARTICLE, "java", "spring", "series", "query");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findPublicPage(eq(ContentType.ARTICLE), eq("java"), eq("spring"),
                eq("series"), eq("query"), eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("publishedAt").getDirection().isDescending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection().isDescending()).isTrue();
        assertThat(result.items()).extracting(com.blog.article.dto.ArticleSummaryResponse::id).containsExactly(1L);
    }

    @Test
    void adminListIncludesWorkflowStateForDraftManagement() {
        Article draft = article(4L, ArticleStatus.SCHEDULED, ContentType.ARTICLE);
        draft.setScheduledAt(NOW.plusSeconds(3600));
        when(articleRepository.findAdminPage(eq(ArticleStatus.SCHEDULED), eq(ContentType.ARTICLE), eq(null),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(draft)));

        var result = articleService.listAdmin(0, 20, ArticleStatus.SCHEDULED, ContentType.ARTICLE, null);

        assertThat(result.items().getFirst().status()).isEqualTo(ArticleStatus.SCHEDULED);
        assertThat(result.items().getFirst().scheduledAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void adminListPassesTrimmedKeywordAndKeepsDeterministicPaginationOrder() {
        when(articleRepository.findAdminPage(eq(null), eq(ContentType.ARTICLE), eq("效率"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(1, 20), 41));
        var result = articleService.listAdmin(1, 20, null, ContentType.ARTICLE, "  效率  ");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findAdminPage(eq(null), eq(ContentType.ARTICLE), eq("效率"), pageable.capture());
        assertThat(result.total()).isEqualTo(41);
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").isDescending()).isTrue();
    }

    @Test
    void bulkLookupUsesOneRepositoryQueryAndPreservesRequestedOrder() {
        when(articleRepository.findAdminSummariesByIdIn(List.of(21L, 3L)))
                .thenReturn(List.of(article(3L, ArticleStatus.DRAFT, ContentType.ARTICLE), article(21L, ArticleStatus.DRAFT, ContentType.ARTICLE)));
        assertThat(articleService.lookupAdmin(List.of(21L, 3L))).extracting(com.blog.article.dto.AdminArticleSummaryResponse::id)
                .containsExactly(21L, 3L);
        verify(articleRepository).findAdminSummariesByIdIn(List.of(21L, 3L));
    }

    private static ArticleWriteRequest request(String title, String markdown, Long coverId, Long categoryId,
                                                Long topicId, Set<Long> tagIds) {
        return new ArticleWriteRequest(title, null, "Summary", markdown, ContentType.ARTICLE,
                coverId, categoryId, topicId, tagIds, "SEO", "SEO description", List.of());
    }

    private static Stream<Arguments> normalizedExpansionRequests() {
        String expansion = "\ufdfa";
        return Stream.of(
                Arguments.of(new ArticleWriteRequest(expansion.repeat(12), null, "Summary", "body",
                        ContentType.ARTICLE, null, null, null, Set.of(), null, null, List.of())),
                Arguments.of(new ArticleWriteRequest("Title", null, expansion.repeat(28), "body",
                        ContentType.ARTICLE, null, null, null, Set.of(), null, null, List.of())),
                Arguments.of(new ArticleWriteRequest("Title", expansion.repeat(9), "Summary", "body",
                        ContentType.ARTICLE, null, null, null, Set.of(), null, null, List.of())),
                Arguments.of(new ArticleWriteRequest("Title", null, "Summary", "body",
                        ContentType.ARTICLE, null, null, null, Set.of(), expansion.repeat(4), null, List.of())),
                Arguments.of(new ArticleWriteRequest("Title", null, "Summary", "body",
                        ContentType.ARTICLE, null, null, null, Set.of(), null, expansion.repeat(9), List.of()))
        );
    }

    private static Article article(Long id, ArticleStatus status, ContentType type) {
        Article article = new Article();
        article.setId(id);
        article.setSlug("article-" + id);
        article.setTitle("Title");
        article.setSummary("Summary");
        article.setMarkdownContent("body");
        article.setRenderedHtml("<p>body</p>");
        article.setContentType(type);
        article.setStatus(status);
        return article;
    }

    private static Category category(Long id) {
        Category category = new Category();
        category.setId(id);
        category.setName("Java");
        category.setSlug("java");
        category.setScope(CategoryScope.ARTICLE);
        return category;
    }

    private static Tag tag(Long id) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName("Spring");
        tag.setSlug("spring");
        return tag;
    }

    private static Topic topic(Long id, TopicStatus status) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName("Series");
        topic.setSlug("series");
        topic.setStatus(status);
        return topic;
    }

    private static MediaAsset image(Long id) {
        MediaAsset media = new MediaAsset();
        media.setId(id);
        media.setStorageKey("cover.png");
        media.setContentType("image/png");
        media.setStatus(MediaStatus.READY);
        media.setPurpose(MediaPurpose.ARTICLE_COVER);
        return media;
    }
}
