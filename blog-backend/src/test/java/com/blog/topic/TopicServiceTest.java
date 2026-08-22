package com.blog.topic;

import com.blog.media.MediaAssetRepository;
import com.blog.taxonomy.SlugAllocationLockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @InjectMocks
    private TopicService topicService;

    @Test
    void reorderStoresTheRequestedArticleOrderAsContiguousPositions() {
        when(topicRepository.findById(3L)).thenReturn(Optional.of(topic(3L)));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(3L)).thenReturn(List.of(
                topicArticle(3L, 22L, 0), topicArticle(3L, 11L, 1)));
        when(topicArticleRepository.countExistingArticlesByIds(List.of(11L, 22L))).thenReturn(2L);

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
}
