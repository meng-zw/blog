package com.blog.media;

import com.blog.article.Article;
import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.site.SiteProfile;
import com.blog.taxonomy.Category;
import com.blog.taxonomy.Tag;
import com.blog.tool.Tool;
import com.blog.tool.ToolRepository;
import com.blog.tool.ToolStatus;
import com.blog.topic.Topic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = ToolMediaConcurrencyMySqlIntegrationTest.JpaConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
class ToolMediaConcurrencyMySqlIntegrationTest {
    private static final String USERNAME = "tool-media-lock-admin";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired ToolRepository toolRepository;
    @Autowired ToolMediaRepository toolMediaRepository;
    @Autowired MediaAssetRepository mediaAssetRepository;
    @Autowired AdminAccountRepository adminAccountRepository;
    @Autowired ToolMediaReferenceService referenceService;
    @Autowired MediaDeletionTransactionService deletionTransactions;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        toolMediaRepository.deleteAll();
        toolRepository.deleteAll();
        mediaAssetRepository.deleteAll();
        adminAccountRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void assignmentAndDeletionSerializeOnTheSharedMediaLock() throws Exception {
        AdminAccount administrator = administrator();
        Tool tool = toolRepository.saveAndFlush(tool());
        MediaAsset media = mediaAssetRepository.saveAndFlush(inlineImage(administrator.getId()));
        CountDownLatch assignmentHasLockedMedia = new CountDownLatch(1);
        CountDownLatch releaseAssignment = new CountDownLatch(1);
        CountDownLatch deletionStarted = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        Future<Void> assignment = executor.submit(() -> {
            transactions.executeWithoutResult(status -> {
                Tool managedTool = toolRepository.findById(tool.getId()).orElseThrow();
                referenceService.synchronize(managedTool, "![image](/api/media/assets/" + media.getId() + ")");
                assignmentHasLockedMedia.countDown();
                await(releaseAssignment);
            });
            return null;
        });
        assertThat(assignmentHasLockedMedia.await(5, TimeUnit.SECONDS)).isTrue();
        Future<Throwable> deletion = executor.submit(() -> {
            try {
                deletionStarted.countDown();
                deletionTransactions.beginOwned(media.getId(), USERNAME);
                return null;
            } catch (Throwable throwable) {
                return throwable;
            }
        });
        assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        assertThat(deletion.isDone()).isFalse();

        releaseAssignment.countDown();

        assignment.get(5, TimeUnit.SECONDS);
        assertThat(deletion.get(5, TimeUnit.SECONDS)).isInstanceOf(com.blog.shared.error.ConflictException.class);
        assertThat(mediaAssetRepository.findById(media.getId()).orElseThrow().getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(toolMediaRepository.findByTool_Id(tool.getId())).extracting(reference -> reference.getMedia().getId())
                .containsExactly(media.getId());
    }

    @Test
    void failedDirectServiceValidationRollsBackWithoutRemovingExistingReferences() {
        AdminAccount administrator = administrator();
        Tool tool = toolRepository.saveAndFlush(tool());
        MediaAsset retained = mediaAssetRepository.saveAndFlush(inlineImage(administrator.getId(), "retained.png"));
        referenceService.synchronize(tool, "![retained](/api/media/assets/" + retained.getId() + ")");
        MediaAsset invalid = inlineImage(administrator.getId(), "invalid.png");
        invalid.setStatus(MediaStatus.PENDING_UPLOAD);
        MediaAsset persistedInvalid = mediaAssetRepository.saveAndFlush(invalid);

        assertThatIllegalArgumentException().isThrownBy(() -> referenceService.synchronize(tool,
                "![invalid](/api/media/assets/" + persistedInvalid.getId() + ")"));

        assertThat(toolMediaRepository.findByTool_Id(tool.getId())).extracting(reference -> reference.getMedia().getId())
                .containsExactly(retained.getId());
    }

    @Test
    void triggerFailureAfterObsoleteDeletionRollsBackToTheCommittedReference() {
        AdminAccount administrator = administrator();
        Tool tool = toolRepository.saveAndFlush(tool());
        MediaAsset oldMedia = mediaAssetRepository.saveAndFlush(inlineImage(administrator.getId(), "old.png"));
        MediaAsset newMedia = mediaAssetRepository.saveAndFlush(inlineImage(administrator.getId(), "new.png"));
        referenceService.synchronize(tool, "![old](/api/media/assets/" + oldMedia.getId() + ")");
        jdbc.execute("DROP TRIGGER IF EXISTS tool_media_reject_new_reference");
        jdbc.execute("""
                CREATE TRIGGER tool_media_reject_new_reference
                BEFORE INSERT ON tool_media
                FOR EACH ROW
                BEGIN
                    IF NEW.tool_id = %d AND NEW.media_id = %d THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'tool_media insert rejected for rollback test';
                    END IF;
                END
                """.formatted(tool.getId(), newMedia.getId()));

        try {
            assertThatThrownBy(() -> referenceService.synchronize(tool,
                    "![new](/api/media/assets/" + newMedia.getId() + ")"))
                    .hasMessageContaining("tool_media insert rejected for rollback test");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS tool_media_reject_new_reference");
        }

        assertThat(toolMediaRepository.findByTool_Id(tool.getId())).extracting(reference -> reference.getMedia().getId())
                .containsExactly(oldMedia.getId());
    }

    private AdminAccount administrator() {
        AdminAccount administrator = new AdminAccount();
        administrator.setUsername(USERNAME);
        administrator.setPasswordHash("not-used-by-this-test");
        administrator.setDisplayName("Tool media lock administrator");
        administrator.setEnabled(true);
        return adminAccountRepository.saveAndFlush(administrator);
    }

    private static Tool tool() {
        Tool tool = new Tool();
        tool.setSlug("tool-media-lock");
        tool.setName("Tool media lock");
        tool.setSummary("summary");
        tool.setDescriptionMarkdown("body");
        tool.setRenderedHtml("<p>body</p>");
        tool.setOfficialUrl("https://example.com");
        tool.setStatus(ToolStatus.DRAFT);
        tool.setTags(Set.of());
        return tool;
    }

    private static MediaAsset inlineImage(long administratorId) {
        return inlineImage(administratorId, "lock.png");
    }

    private static MediaAsset inlineImage(long administratorId, String filename) {
        MediaAsset asset = new MediaAsset();
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        asset.setStorageKey("inline-images/" + filename);
        asset.setStatus(MediaStatus.READY);
        asset.setPurpose(MediaPurpose.INLINE_IMAGE);
        asset.setOriginalFilename(filename);
        asset.setContentType("image/png");
        asset.setByteSize(42L);
        asset.setUploadedById(administratorId);
        asset.setCreatedAt(Instant.parse("2026-08-24T02:00:00Z"));
        asset.setUpdatedAt(Instant.parse("2026-08-24T02:00:00Z"));
        asset.setConfirmedAt(Instant.parse("2026-08-24T02:00:00Z"));
        return asset;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent transaction", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = {Article.class, ArticleMedia.class, Tool.class, ToolMedia.class, MediaAsset.class,
            AdminAccount.class, Category.class, Tag.class, Topic.class, SiteProfile.class})
    @EnableJpaRepositories(basePackageClasses = {ToolRepository.class, ToolMediaRepository.class,
            MediaAssetRepository.class, AdminAccountRepository.class})
    static class JpaConfig {
        @Bean
        ToolMediaReferenceService toolMediaReferenceService(ToolMediaRepository toolMediaRepository,
                                                             MediaAssetRepository mediaAssetRepository) {
            return new ToolMediaReferenceService(toolMediaRepository, mediaAssetRepository);
        }

        @Bean
        MediaReferenceChecker mediaReferenceChecker(jakarta.persistence.EntityManager entityManager) {
            return new MediaReferenceChecker(entityManager);
        }

        @Bean
        MediaDeletionTransactionService mediaDeletionTransactionService(MediaAssetRepository mediaAssetRepository,
                                                                         AdminAccountRepository adminAccountRepository,
                                                                         MediaReferenceChecker mediaReferenceChecker) {
            return new MediaDeletionTransactionService(mediaAssetRepository, adminAccountRepository,
                    mediaReferenceChecker);
        }
    }
}
