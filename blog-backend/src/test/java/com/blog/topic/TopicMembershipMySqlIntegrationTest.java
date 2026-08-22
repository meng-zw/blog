package com.blog.topic;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import com.blog.article.ArticleStatus;
import com.blog.article.ContentType;
import com.blog.media.MediaAsset;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.Tag;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TopicMembershipMySqlIntegrationTest.JpaConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class TopicMembershipMySqlIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired TopicRepository topicRepository;
    @Autowired TopicArticleRepository topicArticleRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired TopicMembershipManager topicMembershipManager;
    @Autowired EntityManager entityManager;

    @Test
    void replacementReinsertsRetainedCompositeIdsMovesArticlesAndCompactsTheOldTopic() {
        Topic target = topic("Target", "target", 0);
        Topic old = topic("Old", "old", 1);
        topicRepository.saveAllAndFlush(List.of(target, old));

        Article retained = article("retained", target);
        Article removed = article("removed", target);
        Article moved = article("moved", old);
        Article oldRemaining = article("old-remaining", old);
        articleRepository.saveAllAndFlush(List.of(retained, removed, moved, oldRemaining));
        topicArticleRepository.saveAllAndFlush(List.of(
                placement(target, retained, 7), placement(target, removed, 8),
                placement(old, moved, 3), placement(old, oldRemaining, 9)));

        topicMembershipManager.replaceTopic(target, List.of(retained, moved));
        entityManager.clear();

        assertThat(articleRepository.findById(retained.getId()).orElseThrow().getTopic().getId())
                .isEqualTo(target.getId());
        assertThat(articleRepository.findById(moved.getId()).orElseThrow().getTopic().getId())
                .isEqualTo(target.getId());
        assertThat(articleRepository.findById(removed.getId()).orElseThrow().getTopic()).isNull();
        assertThat(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(target.getId()))
                .extracting(TopicArticle::getArticleId, TopicArticle::getSortOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(retained.getId(), 0),
                        org.assertj.core.groups.Tuple.tuple(moved.getId(), 1));
        assertThat(topicArticleRepository.findByTopicIdOrderBySortOrderAsc(old.getId()))
                .extracting(TopicArticle::getArticleId, TopicArticle::getSortOrder)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(oldRemaining.getId(), 0));
    }

    private static Topic topic(String name, String slug, int order) {
        Topic topic = new Topic();
        topic.setName(name);
        topic.setNormalizedName(slug);
        topic.setSlug(slug);
        topic.setStatus(TopicStatus.PUBLISHED);
        topic.setSortOrder(order);
        return topic;
    }

    private static Article article(String slug, Topic topic) {
        Article article = new Article();
        article.setSlug(slug);
        article.setTitle(slug);
        article.setSummary(slug);
        article.setMarkdownContent(slug);
        article.setRenderedHtml("<p>" + slug + "</p>");
        article.setContentType(ContentType.ARTICLE);
        article.setStatus(ArticleStatus.DRAFT);
        article.setTopic(topic);
        article.setTags(java.util.Set.of());
        return article;
    }

    private static TopicArticle placement(Topic topic, Article article, int order) {
        TopicArticle placement = new TopicArticle();
        placement.setTopicId(topic.getId());
        placement.setArticleId(article.getId());
        placement.setSortOrder(order);
        return placement;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = {Article.class, Topic.class, TopicArticle.class,
            MediaAsset.class, Category.class, Tag.class})
    @EnableJpaRepositories(basePackageClasses = {ArticleRepository.class, TopicRepository.class,
            TopicArticleRepository.class})
    static class JpaConfig {
        @Bean
        TopicMembershipManager topicMembershipManager(TopicArticleRepository membershipRepository,
                                                       ArticleRepository articleRepository) {
            return new TopicMembershipManager(membershipRepository, articleRepository);
        }
    }
}
