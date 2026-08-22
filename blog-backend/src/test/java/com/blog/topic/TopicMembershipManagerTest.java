package com.blog.topic;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

@ExtendWith(MockitoExtension.class)
class TopicMembershipManagerTest {
    @Mock TopicArticleRepository topicArticleRepository;
    @Mock ArticleRepository articleRepository;

    @Test
    void articleWriteMovesMembershipAndCompactsThePreviousTopic() {
        TopicMembershipManager manager = new TopicMembershipManager(topicArticleRepository, articleRepository);
        Topic target = topic(2L);
        Article moving = article(10L, target);
        TopicArticle old = placement(1L, 10L, 0);
        TopicArticle oldRemaining = placement(1L, 11L, 4);
        when(topicArticleRepository.findByArticleId(10L)).thenReturn(Optional.of(old));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(1L)).thenReturn(List.of(oldRemaining));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(2L)).thenReturn(List.of(placement(2L, 12L, 0)));

        manager.synchronizeArticle(moving);

        verify(topicArticleRepository).deleteByArticleId(10L);
        ArgumentCaptor<TopicArticle> saved = ArgumentCaptor.forClass(TopicArticle.class);
        verify(topicArticleRepository).save(saved.capture());
        assertThat(saved.getValue().getTopicId()).isEqualTo(2L);
        assertThat(saved.getValue().getArticleId()).isEqualTo(10L);
        assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
        assertThat(oldRemaining.getSortOrder()).isZero();
    }

    @Test
    void articleWriteClearsMembershipAndCompactsItsFormerTopic() {
        TopicMembershipManager manager = new TopicMembershipManager(topicArticleRepository, articleRepository);
        Article clearing = article(10L, null);
        TopicArticle remaining = placement(1L, 11L, 8);
        when(topicArticleRepository.findByArticleId(10L)).thenReturn(Optional.of(placement(1L, 10L, 0)));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(1L)).thenReturn(List.of(remaining));

        manager.synchronizeArticle(clearing);

        verify(topicArticleRepository).deleteByArticleId(10L);
        assertThat(remaining.getSortOrder()).isZero();
    }

    @Test
    void topicReplacementMovesArticlesClearsRemovedMembersAndStoresExactOrder() {
        TopicMembershipManager manager = new TopicMembershipManager(topicArticleRepository, articleRepository);
        Topic target = topic(1L);
        Topic other = topic(2L);
        Article removed = article(10L, target);
        Article retained = article(11L, target);
        Article moved = article(12L, other);
        TopicArticle otherRemaining = placement(2L, 13L, 7);
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(1L)).thenReturn(List.of(
                placement(1L, 10L, 0), placement(1L, 11L, 1)));
        when(articleRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(removed, retained));
        when(topicArticleRepository.findByArticleId(11L)).thenReturn(Optional.of(placement(1L, 11L, 1)));
        when(topicArticleRepository.findByArticleId(12L)).thenReturn(Optional.of(placement(2L, 12L, 0)));
        when(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(2L)).thenReturn(List.of(otherRemaining));

        manager.replaceTopic(target, List.of(retained, moved));

        assertThat(removed.getTopic()).isNull();
        assertThat(retained.getTopic()).isSameAs(target);
        assertThat(moved.getTopic()).isSameAs(target);
        assertThat(otherRemaining.getSortOrder()).isZero();
        ArgumentCaptor<List<TopicArticle>> placements = ArgumentCaptor.forClass(List.class);
        verify(topicArticleRepository, atLeastOnce()).saveAll(placements.capture());
        List<TopicArticle> exact = placements.getAllValues().getLast();
        assertThat(exact).extracting(TopicArticle::getArticleId).containsExactly(11L, 12L);
        assertThat(exact).extracting(TopicArticle::getSortOrder).containsExactly(0, 1);
    }

    private static Topic topic(Long id) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName("Topic " + id);
        topic.setSlug("topic-" + id);
        topic.setStatus(TopicStatus.PUBLISHED);
        return topic;
    }

    private static Article article(Long id, Topic topic) {
        Article article = new Article();
        article.setId(id);
        article.setTopic(topic);
        return article;
    }

    private static TopicArticle placement(Long topicId, Long articleId, int order) {
        TopicArticle placement = new TopicArticle();
        placement.setTopicId(topicId);
        placement.setArticleId(articleId);
        placement.setSortOrder(order);
        return placement;
    }
}
