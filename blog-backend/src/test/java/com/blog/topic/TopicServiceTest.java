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

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @InjectMocks
    private TopicService topicService;

    @Test
    void reorderStoresTheRequestedArticleOrderAsContiguousPositions() {
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic(3L)));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(3L)).thenReturn(List.of(
                topicArticle(3L, 22L, 0), topicArticle(3L, 11L, 1)));
        when(articleRepository.findAllById(List.of(11L, 22L))).thenReturn(List.of(
                article(11L, ArticleStatus.DRAFT, null), article(22L, ArticleStatus.DRAFT, null)));

        topicService.reorderArticles(3L, List.of(11L, 22L));

        ArgumentCaptor<List<TopicArticle>> saved = ArgumentCaptor.forClass(List.class);
        verify(topicArticleRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(TopicArticle::getArticleId).containsExactly(11L, 22L);
        assertThat(saved.getValue()).extracting(TopicArticle::getSortOrder).containsExactly(0, 1);
    }

    @Test
    void reorderRejectsAnIncompleteArticleListBeforeChangingTheStoredOrder() {
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic(3L)));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(3L)).thenReturn(List.of(
                topicArticle(3L, 11L, 0), topicArticle(3L, 22L, 1)));

        assertThatIllegalArgumentException().isThrownBy(() -> topicService.reorderArticles(3L, List.of(11L)));
        verify(topicArticleRepository, never()).saveAll(any());
    }

    @Test
    void reorderRejectsDuplicateArticleIdsBeforeChangingTheStoredOrder() {
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic(3L)));

        assertThatIllegalArgumentException().isThrownBy(() -> topicService.reorderArticles(3L, List.of(11L, 11L)));
        verify(topicArticleRepository, never()).saveAll(any());
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
        verify(topicArticleRepository).saveAll(any());
    }

    @Test
    void updateWithEmptyListExplicitlyClearsArticles() {
        Topic existing = topic(3L);
        existing.setNormalizedName("java");
        when(topicRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.of(existing));
        when(topicRepository.save(existing)).thenReturn(existing);
        topicService.update(3L, new TopicWriteRequest("Java", null, null, TopicStatus.DRAFT, List.of(), 0));
        verify(topicArticleRepository).deleteByTopicId(3L);
    }

    @Test
    void createRejectsUnknownArticleIdsBeforeAssociationMutation() {
        when(topicRepository.findByNormalizedName("java")).thenReturn(Optional.empty());
        when(topicRepository.existsBySlug("java")).thenReturn(false);
        when(topicRepository.save(any(Topic.class))).thenAnswer(i -> { Topic value = i.getArgument(0); value.setId(3L); return value; });
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

        ArgumentCaptor<List<TopicArticle>> saved = ArgumentCaptor.forClass(List.class);
        verify(topicArticleRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(TopicArticle::getArticleId).containsExactly(22L, 11L);
        assertThat(saved.getValue()).extracting(TopicArticle::getSortOrder).containsExactly(0, 1);
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
