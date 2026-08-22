package com.blog.topic;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TopicMembershipManager {
    private final TopicArticleRepository topicArticleRepository;
    private final ArticleRepository articleRepository;

    public TopicMembershipManager(TopicArticleRepository topicArticleRepository, ArticleRepository articleRepository) {
        this.topicArticleRepository = topicArticleRepository;
        this.articleRepository = articleRepository;
    }

    public void synchronizeArticle(Article article) {
        TopicArticle existing = topicArticleRepository.findByArticleId(article.getId()).orElse(null);
        Long targetTopicId = article.getTopic() == null ? null : article.getTopic().getId();
        if (existing != null && existing.getTopicId().equals(targetTopicId)) {
            return;
        }
        if (existing != null) {
            articleRepository.saveAndFlush(article);
            topicArticleRepository.deleteByArticleId(article.getId());
            articleRepository.saveAndFlush(article);
            compact(existing.getTopicId());
        } else if (targetTopicId != null) {
            articleRepository.saveAndFlush(article);
        }
        if (targetTopicId != null) {
            int order = compact(targetTopicId);
            topicArticleRepository.saveAllAndFlush(List.of(placement(targetTopicId, article.getId(), order)));
        }
    }

    public void replaceTopic(Topic topic, List<Article> requested) {
        List<TopicArticle> currentPlacements = topicArticleRepository
                .findByTopicIdOrderBySortOrderAsc(topic.getId());
        List<Long> currentIds = currentPlacements.stream().map(TopicArticle::getArticleId).toList();
        List<Article> currentArticles = currentIds.isEmpty() ? List.of() : articleRepository.findAllById(currentIds);

        Map<Long, TopicArticle> requestedExisting = new LinkedHashMap<>();
        for (Article article : requested) {
            topicArticleRepository.findByArticleId(article.getId())
                    .ifPresent(placement -> requestedExisting.put(article.getId(), placement));
        }

        Set<Long> requestedIds = requested.stream().map(Article::getId)
                .collect(java.util.stream.Collectors.toSet());
        for (Article article : currentArticles) {
            if (!requestedIds.contains(article.getId()) && article.getTopic() != null
                    && article.getTopic().getId().equals(topic.getId())) {
                article.setTopic(null);
            }
        }
        for (Article article : requested) {
            article.setTopic(topic);
        }

        Map<Long, Article> changed = new LinkedHashMap<>();
        currentArticles.forEach(article -> changed.put(article.getId(), article));
        requested.forEach(article -> changed.put(article.getId(), article));
        if (!changed.isEmpty()) {
            articleRepository.saveAllAndFlush(changed.values());
        }

        topicArticleRepository.deleteByTopicId(topic.getId());
        Set<Long> compactTopics = new LinkedHashSet<>();
        for (Article article : requested) {
            TopicArticle existing = requestedExisting.get(article.getId());
            if (existing != null && !existing.getTopicId().equals(topic.getId())) {
                topicArticleRepository.deleteByArticleId(article.getId());
                compactTopics.add(existing.getTopicId());
            }
        }

        if (!changed.isEmpty()) {
            articleRepository.saveAllAndFlush(changed.values());
        }
        compactTopics.forEach(this::compact);
        List<TopicArticle> replacement = placements(topic.getId(), requested);
        if (!replacement.isEmpty()) {
            topicArticleRepository.saveAllAndFlush(replacement);
        }
    }

    private int compact(long topicId) {
        List<TopicArticle> placements = topicArticleRepository.findByTopicIdOrderBySortOrderAsc(topicId);
        for (int index = 0; index < placements.size(); index++) {
            placements.get(index).setSortOrder(index);
        }
        if (!placements.isEmpty()) {
            topicArticleRepository.saveAllAndFlush(placements);
        }
        return placements.size();
    }

    private static TopicArticle placement(Long topicId, Long articleId, int order) {
        TopicArticle placement = new TopicArticle();
        placement.setTopicId(topicId);
        placement.setArticleId(articleId);
        placement.setSortOrder(order);
        return placement;
    }

    private static List<TopicArticle> placements(Long topicId, List<Article> articles) {
        List<TopicArticle> placements = new ArrayList<>(articles.size());
        for (int index = 0; index < articles.size(); index++) {
            placements.add(placement(topicId, articles.get(index).getId(), index));
        }
        return placements;
    }
}
