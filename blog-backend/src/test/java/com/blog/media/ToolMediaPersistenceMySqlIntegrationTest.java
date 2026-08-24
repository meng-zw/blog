package com.blog.media;

import com.blog.article.MarkdownRenderer;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.CategoryRepository;
import com.blog.taxonomy.SlugAllocationLock;
import com.blog.taxonomy.SlugAllocationLockRepository;
import com.blog.taxonomy.Tag;
import com.blog.taxonomy.TagRepository;
import com.blog.taxonomy.TaxonomyService;
import com.blog.tool.Tool;
import com.blog.tool.ToolRepository;
import com.blog.tool.ToolService;
import com.blog.tool.ToolStatus;
import com.blog.tool.dto.ToolWriteRequest;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ToolMediaPersistenceMySqlIntegrationTest.JpaConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ToolMediaPersistenceMySqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired ToolRepository toolRepository;
    @Autowired MediaAssetRepository mediaAssetRepository;
    @Autowired ToolMediaRepository toolMediaRepository;
    @Autowired ToolMediaReferenceService referenceService;
    @Autowired ToolService toolService;
    @Autowired EntityManager entityManager;

    @Test
    void retainsAnUnchangedImageRowThroughAToolUpdate() {
        Tool tool = toolRepository.saveAndFlush(tool());
        MediaAsset image = mediaAssetRepository.saveAndFlush(inlineImage());
        referenceService.synchronize(tool, "![image](/api/media/assets/" + image.getId() + ")");
        entityManager.flush();
        entityManager.clear();
        Instant createdAt = toolMediaRepository.findByTool_Id(tool.getId()).getFirst().getCreatedAt();

        toolService.update(tool.getId(), new ToolWriteRequest("Updated tool", null, "Updated summary",
                "![image](/api/media/assets/" + image.getId() + ")", "https://example.com", null, null,
                Set.of(), false));
        entityManager.flush();
        entityManager.clear();

        List<ToolMedia> references = toolMediaRepository.findByTool_Id(tool.getId());
        assertThat(references).hasSize(1);
        assertThat(references.getFirst().getMedia().getId()).isEqualTo(image.getId());
        assertThat(references.getFirst().getCreatedAt()).isEqualTo(createdAt);
    }

    private static Tool tool() {
        Tool tool = new Tool();
        tool.setSlug("tool-media-persistence");
        tool.setName("Tool media persistence");
        tool.setSummary("summary");
        tool.setDescriptionMarkdown("body");
        tool.setRenderedHtml("<p>body</p>");
        tool.setOfficialUrl("https://example.com");
        tool.setStatus(ToolStatus.DRAFT);
        tool.setTags(Set.of());
        return tool;
    }

    private static MediaAsset inlineImage() {
        MediaAsset asset = new MediaAsset();
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        asset.setStorageKey("inline-images/tool.png");
        asset.setStatus(MediaStatus.READY);
        asset.setPurpose(MediaPurpose.INLINE_IMAGE);
        asset.setOriginalFilename("tool.png");
        asset.setContentType("image/png");
        asset.setByteSize(42L);
        asset.setCreatedAt(NOW);
        asset.setUpdatedAt(NOW);
        asset.setConfirmedAt(NOW);
        return asset;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = {Tool.class, ToolMedia.class, MediaAsset.class, Category.class, Tag.class,
            SlugAllocationLock.class})
    @EnableJpaRepositories(basePackageClasses = {ToolRepository.class, ToolMediaRepository.class,
            MediaAssetRepository.class, CategoryRepository.class, TagRepository.class, SlugAllocationLockRepository.class})
    static class JpaConfig {
        @Bean
        Clock mediaClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ToolMediaReferenceService toolMediaReferenceService(ToolMediaRepository toolMediaRepository,
                                                             MediaAssetRepository mediaAssetRepository,
                                                             Clock mediaClock) {
            return new ToolMediaReferenceService(toolMediaRepository, mediaAssetRepository,
                    new StableMediaReferenceParser(), mediaClock);
        }

        @Bean
        TaxonomyService taxonomyService(CategoryRepository categoryRepository, TagRepository tagRepository,
                                        SlugAllocationLockRepository slugAllocationLockRepository) {
            return new TaxonomyService(categoryRepository, tagRepository, slugAllocationLockRepository);
        }

        @Bean
        ToolService toolService(ToolRepository toolRepository, MarkdownRenderer markdownRenderer,
                                TaxonomyService taxonomyService, MediaAssetRepository mediaAssetRepository,
                                SlugAllocationLockRepository slugAllocationLockRepository,
                                ToolMediaReferenceService toolMediaReferenceService) {
            return new ToolService(toolRepository, markdownRenderer, taxonomyService, mediaAssetRepository,
                    slugAllocationLockRepository, toolMediaReferenceService);
        }

        @Bean
        MarkdownRenderer markdownRenderer() {
            return new MarkdownRenderer();
        }
    }
}
