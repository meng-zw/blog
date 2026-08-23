package com.blog.topic;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import com.blog.article.ArticleStatus;
import com.blog.article.ContentType;
import com.blog.media.MediaAssetRepository;
import com.blog.media.MediaAsset;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.topic.dto.TopicWriteRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicArticleRepository topicArticleRepository;
    @Mock
    private MediaAssetRepository mediaAssetRepository;
    @Mock
    private SlugAllocationLockRepository slugAllocationLockRepository;
    @Mock
    private ArticleRepository articleRepository;
    @Mock
    private TopicMembershipManager topicMembershipManager;
    @InjectMocks
    private TopicService topicService;

    @Test
    void adminPageLoadsMembershipsInOneBulkQuery() {
        Topic first = topic(1L), second = topic(2L);
        when(topicRepository.findAdminPage(null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
        when(topicArticleRepository.findByTopicIdInOrderByTopicIdAscSortOrderAsc(List.of(1L, 2L)))
                .thenReturn(List.of(topicArticle(1L, 11L, 0), topicArticle(2L, 22L, 0)));

        var page = topicService.listAdmin(0, 20, null, null);

        assertThat(page.items()).extracting(item -> item.articleIds()).containsExactly(List.of(11L), List.of(22L));
        verify(topicArticleRepository).findByTopicIdInOrderByTopicIdAscSortOrderAsc(List.of(1L, 2L));
        verify(topicArticleRepository, never()).findByTopicIdOrderBySortOrderAsc(anyLong());
    }

    @Test
    void adminPageCombinesStatusKeywordPaginationAndBulkMemberships() {
        var pageable = PageRequest.of(1, 20);
        when(topicRepository.findAdminPage(TopicStatus.PUBLISHED, "效率", pageable))
                .thenReturn(new PageImpl<>(List.of(topic(2L)), pageable, 21));
        when(topicArticleRepository.findByTopicIdInOrderByTopicIdAscSortOrderAsc(List.of(2L))).thenReturn(List.of());
        var result = topicService.listAdmin(1, 20, TopicStatus.PUBLISHED, "  效率  ");
        assertThat(result.total()).isEqualTo(21);
        verify(topicRepository).findAdminPage(TopicStatus.PUBLISHED, "效率", pageable);
    }

    @Test
    void reorderStoresTheRequestedArticleOrderAsContiguousPositions() {
        Topic topic = topic(3L);
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(3L)).thenReturn(List.of(
                topicArticle(3L, 22L, 0), topicArticle(3L, 11L, 1)));
        when(articleRepository.findAllById(List.of(11L, 22L))).thenReturn(List.of(
                article(11L, ArticleStatus.DRAFT, null), article(22L, ArticleStatus.DRAFT, null)));

        topicService.reorderArticles(3L, List.of(11L, 22L));

        ArgumentCaptor<List<Article>> saved = ArgumentCaptor.forClass(List.class);
        verify(topicMembershipManager).replaceTopic(eq(topic), saved.capture());
        assertThat(saved.getValue()).extracting(Article::getId).containsExactly(11L, 22L);
    }

    @Test
    void reorderRejectsAnIncompleteArticleListBeforeChangingTheStoredOrder() {
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic(3L)));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(3L)).thenReturn(List.of(
                topicArticle(3L, 11L, 0), topicArticle(3L, 22L, 1)));

        assertThatIllegalArgumentException().isThrownBy(() -> topicService.reorderArticles(3L, List.of(11L)));
        verify(topicMembershipManager, never()).replaceTopic(any(), any());
    }

    @Test
    void reorderRejectsDuplicateArticleIdsBeforeChangingTheStoredOrder() {
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic(3L)));

        assertThatIllegalArgumentException().isThrownBy(() -> topicService.reorderArticles(3L, List.of(11L, 11L)));
        verify(topicMembershipManager, never()).replaceTopic(any(), any());
    }

    @Test
    void createRejectsOmittedArticleIds() {
        assertThatIllegalArgumentException().isThrownBy(() -> topicService.create(
                new com.blog.topic.dto.TopicWriteRequest("Java", null, null, TopicStatus.DRAFT, null, 0)));
    }

    @Test
    void createPersistsContiguousReplacementAndLocksBeforeRepositoryRead() {
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.empty());
        when(topicRepository.existsBySlug("java")).thenReturn(false);
        when(topicRepository.save(any(Topic.class))).thenAnswer(i -> { Topic value = i.getArgument(0); value.setId(3L); return value; });
        when(articleRepository.findAllById(List.of(11L, 22L))).thenReturn(List.of(
                article(11L, ArticleStatus.DRAFT, null), article(22L, ArticleStatus.DRAFT, null)));
        topicService.create(new TopicWriteRequest("Java", null, null, TopicStatus.DRAFT, List.of(11L, 22L), 0));
        var order = inOrder(slugAllocationLockRepository, topicRepository);
        order.verify(slugAllocationLockRepository).lockSingleton();
        order.verify(topicRepository).findByNormalizedName("java");
        verify(topicMembershipManager).replaceTopic(any(Topic.class), any());
    }

    @Test
    void updateWithEmptyListExplicitlyClearsArticles() {
        Topic existing = topic(3L);
        existing.setNormalizedName("java");
        when(topicRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.of(existing));
        when(topicRepository.save(existing)).thenReturn(existing);
        topicService.update(3L, new TopicWriteRequest("Java", null, null, TopicStatus.DRAFT, List.of(), 0));
        verify(topicMembershipManager).replaceTopic(existing, List.of());
    }

    @Test
    void createRejectsUnknownArticleIdsBeforeAssociationMutation() {
        when(articleRepository.findAllById(List.of(99L))).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> topicService.create(
                new TopicWriteRequest("Java", null, null, TopicStatus.DRAFT, List.of(99L), 0)))
                .isInstanceOf(com.blog.shared.error.ResourceNotFoundException.class);
    }

    @Test
    void invalidCoverMediaIsRejected() {
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.empty());
        when(topicRepository.existsBySlug("java")).thenReturn(false);
        when(mediaAssetRepository.findById(8L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> topicService.create(
                new TopicWriteRequest("Java", null, 8L, TopicStatus.DRAFT, List.of(), 0)))
                .isInstanceOf(com.blog.shared.error.ResourceNotFoundException.class);
    }

    @Test
    void updateReplacesWithCredibleArticleIdsAndStoresExactContiguousOrdering() {
        Topic existing = topic(3L);
        existing.setNormalizedName("java");
        when(topicRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.of(existing));
        when(topicRepository.save(existing)).thenReturn(existing);
        when(articleRepository.findAllById(List.of(22L, 11L))).thenReturn(List.of(
                article(22L, ArticleStatus.DRAFT, null),
                article(11L, ArticleStatus.PUBLISHED, Instant.parse("2026-08-22T09:00:00Z"))));

        topicService.update(3L, new TopicWriteRequest("Java", null, null, TopicStatus.PUBLISHED,
                List.of(22L, 11L), 0));

        ArgumentCaptor<List<Article>> saved = ArgumentCaptor.forClass(List.class);
        verify(topicMembershipManager).replaceTopic(eq(existing), saved.capture());
        assertThat(saved.getValue()).extracting(Article::getId).containsExactly(22L, 11L);
    }

    @Test
    void publishedTopicDetailExcludesDraftAndFutureArticlesFromComposition() {
        Topic publishedTopic = topic(3L);
        publishedTopic.setStatus(TopicStatus.PUBLISHED);
        when(topicRepository.findBySlug("java")).thenReturn(Optional.of(publishedTopic));
        when(articleRepository.findVisibleForTopic(eq(3L), any())).thenReturn(List.of(
                article(22L, ArticleStatus.DRAFT, null),
                article(11L, ArticleStatus.PUBLISHED, Instant.parse("2026-08-22T09:00:00Z")),
                article(33L, ArticleStatus.PUBLISHED, Instant.parse("2999-01-01T00:00:00Z"))));

        var detail = topicService.findPublishedDetailBySlug("java");

        assertThat(detail.articles()).extracting(com.blog.article.dto.ArticleSummaryResponse::id)
                .containsExactly(11L);
        assertThat(detail.slug()).isEqualTo("java");
    }

    @Test
    void publicTopicPageIsBoundedOrderedAndContainsOnlyPublicMetadata() {
        Topic published = topic(7L);
        published.setStatus(TopicStatus.PUBLISHED);
        published.setSortOrder(4);
        when(topicRepository.findPublishedPage(PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(List.of(published), PageRequest.of(1, 2), 5));

        var page = topicService.listPublished(1, 2);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.total()).isEqualTo(5);
        assertThat(page.items()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(7L);
            assertThat(summary.slug()).isEqualTo("java");
        });
        verify(topicRepository).findPublishedPage(PageRequest.of(1, 2));
    }

    @Test
    void publicTopicPageRejectsOutOfRangePaginationBeforeRepositoryAccess() {
        assertThatIllegalArgumentException().isThrownBy(() -> topicService.listPublished(-1, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> topicService.listPublished(0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> topicService.listPublished(0, 51));
        verify(topicRepository, never()).findPublishedPage(any());
    }

    @Test
    void draftTopicCannotBeLookedUpThroughPublishedDetail() {
        when(topicRepository.findBySlug("draft-series")).thenReturn(Optional.of(topic(3L)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> topicService.findPublishedDetailBySlug("draft-series"))
                .isInstanceOf(com.blog.shared.error.ResourceNotFoundException.class);
        verify(articleRepository, never()).findVisibleForTopic(any(Long.class), any());
    }

    @Test
    void validImageCoverIsReturnedAsPublicMediaUrl() {
        MediaAsset cover = new MediaAsset();
        cover.setId(8L);
        cover.setStorageKey("topics/java.png");
        cover.setContentType("image/png");
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.empty());
        when(topicRepository.existsBySlug("java")).thenReturn(false);
        when(mediaAssetRepository.findById(8L)).thenReturn(Optional.of(cover));
        when(topicRepository.save(any(Topic.class))).thenAnswer(call -> {
            Topic value = call.getArgument(0);
            value.setId(3L);
            return value;
        });

        var created = topicService.create(new TopicWriteRequest(
                "Java", null, 8L, TopicStatus.PUBLISHED, List.of(), 0));

        assertThat(created.coverUrl()).isEqualTo("/api/media/topics/java.png");
    }

    @Test
    void nonImageTopicCoverIsRejectedBeforeTopicSave() {
        MediaAsset pdf = new MediaAsset();
        pdf.setId(8L);
        pdf.setStorageKey("document.pdf");
        pdf.setContentType("application/pdf");
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.empty());
        when(topicRepository.existsBySlug("java")).thenReturn(false);
        when(mediaAssetRepository.findById(8L)).thenReturn(Optional.of(pdf));

        assertThatIllegalArgumentException().isThrownBy(() -> topicService.create(new TopicWriteRequest(
                "Java", null, 8L, TopicStatus.PUBLISHED, List.of(), 0)));

        verify(topicRepository, never()).save(any());
    }

    private static Topic topic(Long id) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName("Java");
        topic.setSlug("java");
        topic.setStatus(TopicStatus.DRAFT);
        return topic;
    }

    private static TopicArticle topicArticle(Long topicId, Long articleId, int sortOrder) {
        TopicArticle article = new TopicArticle();
        article.setTopicId(topicId);
        article.setArticleId(articleId);
        article.setSortOrder(sortOrder);
        return article;
    }

    private static Article article(Long id, ArticleStatus status, Instant publishedAt) {
        Article article = new Article();
        article.setId(id);
        article.setSlug("article-" + id);
        article.setTitle("Article " + id);
        article.setSummary("Summary " + id);
        article.setContentType(ContentType.ARTICLE);
        article.setStatus(status);
        article.setPublishedAt(publishedAt);
        article.setTags(java.util.Set.of());
        return article;
    }
}
