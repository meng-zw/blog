package com.blog.media;

import com.blog.article.Article;
import com.blog.article.ArticleRepository;
import com.blog.article.ArticleStatus;
import com.blog.article.ContentType;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.Tag;
import com.blog.topic.Topic;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ArticleMediaPersistenceMySqlIntegrationTest.JpaConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ArticleMediaPersistenceMySqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired ArticleRepository articleRepository;
    @Autowired MediaAssetRepository mediaAssetRepository;
    @Autowired ArticleMediaRepository articleMediaRepository;
    @Autowired ArticleMediaReferenceService referenceService;
    @Autowired EntityManager entityManager;

    @Test
    void retainsCompositeIdsAndPersistsAttachmentReorderingAcrossFlushes() {
        Article article = articleRepository.saveAndFlush(article());
        MediaAsset first = mediaAssetRepository.saveAndFlush(attachment("first.pdf", "attachments/first.pdf"));
        MediaAsset second = mediaAssetRepository.saveAndFlush(attachment("second.pdf", "attachments/second.pdf"));

        referenceService.synchronize(article, "body", List.of(first.getId(), second.getId()));
        entityManager.flush();
        entityManager.clear();
        Instant originalCreatedAt = articleMediaRepository
                .findByArticle_IdAndId_RoleOrderBySortOrderAsc(article.getId(), ArticleMediaRole.ATTACHMENT)
                .getFirst().getCreatedAt();

        Article managedArticle = articleRepository.findById(article.getId()).orElseThrow();
        referenceService.synchronize(managedArticle, "body", List.of(second.getId(), first.getId()));
        entityManager.flush();
        entityManager.clear();

        List<ArticleMedia> reordered = articleMediaRepository
                .findByArticle_IdAndId_RoleOrderBySortOrderAsc(article.getId(), ArticleMediaRole.ATTACHMENT);
        assertThat(reordered).extracting(reference -> reference.getMedia().getId(), ArticleMedia::getSortOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(second.getId(), 0),
                        org.assertj.core.groups.Tuple.tuple(first.getId(), 1));
        assertThat(reordered).extracting(ArticleMedia::getCreatedAt).containsOnly(originalCreatedAt);
    }

    private static Article article() {
        Article article = new Article();
        article.setSlug("persistence-media");
        article.setTitle("persistence-media");
        article.setSummary("summary");
        article.setMarkdownContent("body");
        article.setRenderedHtml("<p>body</p>");
        article.setContentType(ContentType.ARTICLE);
        article.setStatus(ArticleStatus.DRAFT);
        article.setTags(java.util.Set.of());
        return article;
    }

    private static MediaAsset attachment(String filename, String key) {
        MediaAsset asset = new MediaAsset();
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        asset.setStorageKey(key);
        asset.setStatus(MediaStatus.READY);
        asset.setPurpose(MediaPurpose.ATTACHMENT);
        asset.setOriginalFilename(filename);
        asset.setContentType("application/pdf");
        asset.setByteSize(42L);
        asset.setCreatedAt(NOW);
        asset.setUpdatedAt(NOW);
        asset.setConfirmedAt(NOW);
        return asset;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = {Article.class, MediaAsset.class, ArticleMedia.class,
            Category.class, Tag.class, Topic.class})
    @EnableJpaRepositories(basePackageClasses = {ArticleRepository.class, MediaAssetRepository.class,
            ArticleMediaRepository.class})
    static class JpaConfig {
        @Bean
        ArticleMediaReferenceService articleMediaReferenceService(ArticleMediaRepository articleMediaRepository,
                                                                   MediaAssetRepository mediaAssetRepository) {
            return new ArticleMediaReferenceService(articleMediaRepository, mediaAssetRepository,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
