package com.blog.media;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.shared.error.ConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MediaOperationConcurrencyMySqlIntegrationTest.JpaConfig.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.task.scheduling.enabled=false"
        })
@Testcontainers(disabledWithoutDocker = true)
class MediaOperationConcurrencyMySqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final Instant EXPIRED_AT = NOW.minusSeconds(25 * 60 * 60);
    private static final String USERNAME = "media-race-admin";
    private static final int RACE_ITERATIONS = 12;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired MediaAssetRepository mediaRepository;
    @Autowired AdminAccountRepository adminRepository;
    @Autowired MediaOperationTransactionService operationTransactions;
    @Autowired MediaDeletionTransactionService deletionTransactions;

    private Long administratorId;

    @BeforeEach
    void resetDatabase() {
        mediaRepository.deleteAll();
        adminRepository.deleteAll();

        AdminAccount administrator = new AdminAccount();
        administrator.setUsername(USERNAME);
        administrator.setPasswordHash("not-used-by-this-test");
        administrator.setDisplayName("Media race admin");
        administrator.setEnabled(true);
        administratorId = adminRepository.saveAndFlush(administrator).getId();
    }

    @Test
    void verificationAndExpiredCleanupCannotBothClaimTheSameObject() throws Exception {
        for (int iteration = 0; iteration < RACE_ITERATIONS; iteration++) {
            MediaAsset asset = mediaRepository.saveAndFlush(expiredPendingAsset(iteration));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<VerificationOutcome> verification = executor.submit(() -> {
                    start.await();
                    try {
                        MediaOperationTransactionService.OperationClaim claim =
                                operationTransactions.claimVerification(asset.getId(), USERNAME);
                        return VerificationOutcome.claimed(claim.operationToken());
                    } catch (RuntimeException exception) {
                        return VerificationOutcome.rejected(exception);
                    }
                });
                Future<Optional<MediaDeletionTransactionService.DeletionTarget>> cleanup = executor.submit(() -> {
                    start.await();
                    return deletionTransactions.claimCleanup(asset.getId(), NOW);
                });

                start.countDown();
                VerificationOutcome verificationOutcome = verification.get(10, TimeUnit.SECONDS);
                Optional<MediaDeletionTransactionService.DeletionTarget> cleanupOutcome =
                        cleanup.get(10, TimeUnit.SECONDS);

                MediaAsset persisted = mediaRepository.findById(asset.getId()).orElseThrow();
                if (verificationOutcome.claimed()) {
                    assertThat(cleanupOutcome)
                            .as("iteration %s must not expose a verification-owned object to cleanup", iteration)
                            .isEmpty();
                    assertThat(persisted.getStatus()).isEqualTo(MediaStatus.VERIFYING);
                    assertThat(persisted.getOperationToken()).isEqualTo(verificationOutcome.operationToken());
                } else {
                    assertThat(verificationOutcome.rejection())
                            .as("iteration %s verification must lose only to the committed cleanup state", iteration)
                            .isInstanceOf(ConflictException.class);
                    assertThat(cleanupOutcome)
                            .as("iteration %s must have a single cleanup owner when verification loses", iteration)
                            .isPresent();
                    assertThat(cleanupOutcome.orElseThrow().requiresObjectDelete()).isTrue();
                    assertThat(persisted.getStatus()).isEqualTo(MediaStatus.ABANDONED);
                    assertThat(persisted.getOperationToken()).isNull();
                }
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void proxyUploadAndExpiredCleanupCannotBothClaimTheSameObject() throws Exception {
        for (int iteration = 0; iteration < RACE_ITERATIONS; iteration++) {
            MediaAsset asset = mediaRepository.saveAndFlush(expiredPendingAsset(100 + iteration));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<VerificationOutcome> upload = executor.submit(() -> {
                    start.await();
                    try {
                        MediaOperationTransactionService.OperationClaim claim =
                                operationTransactions.claimProxyUpload(asset.getId(), USERNAME);
                        return VerificationOutcome.claimed(claim.operationToken());
                    } catch (RuntimeException exception) {
                        return VerificationOutcome.rejected(exception);
                    }
                });
                Future<Optional<MediaDeletionTransactionService.DeletionTarget>> cleanup = executor.submit(() -> {
                    start.await();
                    return deletionTransactions.claimCleanup(asset.getId(), NOW);
                });

                start.countDown();
                VerificationOutcome uploadOutcome = upload.get(10, TimeUnit.SECONDS);
                Optional<MediaDeletionTransactionService.DeletionTarget> cleanupOutcome =
                        cleanup.get(10, TimeUnit.SECONDS);

                MediaAsset persisted = mediaRepository.findById(asset.getId()).orElseThrow();
                if (uploadOutcome.claimed()) {
                    assertThat(cleanupOutcome)
                            .as("iteration %s must not expose an upload-owned object to cleanup", iteration)
                            .isEmpty();
                    assertThat(persisted.getStatus()).isEqualTo(MediaStatus.UPLOADING);
                    assertThat(persisted.getOperationToken()).isEqualTo(uploadOutcome.operationToken());
                } else {
                    assertThat(uploadOutcome.rejection()).isInstanceOf(ConflictException.class);
                    assertThat(cleanupOutcome).isPresent();
                    assertThat(persisted.getStatus()).isEqualTo(MediaStatus.ABANDONED);
                    assertThat(persisted.getOperationToken()).isNull();
                }
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = MediaStatus.class, names = {"UPLOADING", "VERIFYING"})
    void expiredCrashClaimsBecomeRecoverableCleanupTargets(MediaStatus crashedStatus) {
        MediaAsset asset = expiredPendingAsset(200 + crashedStatus.ordinal());
        asset.setProvider(StorageProvider.CLOUDREVE);
        asset.setBucket("cloudreve://my/blog/media");
        asset.setStorageKey("inline-images/2026/08/123e4567-e89b-12d3-a456-42661417400"
                + crashedStatus.ordinal() + ".png");
        asset.setStatus(crashedStatus);
        asset.setOperationToken("crashed-operation-token");
        asset = mediaRepository.saveAndFlush(asset);

        Optional<MediaDeletionTransactionService.DeletionTarget> cleanup =
                deletionTransactions.claimCleanup(asset.getId(), NOW);

        assertThat(cleanup).isPresent();
        assertThat(cleanup.orElseThrow().location().provider()).isEqualTo(StorageProvider.CLOUDREVE);
        MediaAsset persisted = mediaRepository.findById(asset.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(MediaStatus.ABANDONED);
        assertThat(persisted.getOperationToken()).isNull();
    }

    private MediaAsset expiredPendingAsset(int iteration) {
        MediaAsset asset = new MediaAsset();
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        asset.setStorageKey("race/" + iteration + ".png");
        asset.setStatus(MediaStatus.PENDING_UPLOAD);
        asset.setPurpose(MediaPurpose.INLINE_IMAGE);
        asset.setOriginalFilename("race-" + iteration + ".png");
        asset.setContentType("image/png");
        asset.setByteSize(42L);
        asset.setUploadedById(administratorId);
        asset.setCreatedAt(EXPIRED_AT);
        asset.setUpdatedAt(EXPIRED_AT);
        return asset;
    }

    private record VerificationOutcome(boolean claimed, String operationToken, RuntimeException rejection) {
        private static VerificationOutcome claimed(String operationToken) {
            return new VerificationOutcome(true, operationToken, null);
        }

        private static VerificationOutcome rejected(RuntimeException rejection) {
            return new VerificationOutcome(false, null, rejection);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = {MediaAsset.class, AdminAccount.class})
    @EnableJpaRepositories(basePackageClasses = {MediaAssetRepository.class, AdminAccountRepository.class})
    static class JpaConfig {
        @Bean
        Clock mediaClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        MediaReferenceChecker mediaReferenceChecker(EntityManager entityManager) {
            return new MediaReferenceChecker(entityManager);
        }

        @Bean
        MediaOperationTransactionService mediaOperationTransactionService(MediaAssetRepository mediaRepository,
                                                                           AdminAccountRepository adminRepository,
                                                                           Clock mediaClock) {
            return new MediaOperationTransactionService(mediaRepository, adminRepository, mediaClock);
        }

        @Bean
        MediaDeletionTransactionService mediaDeletionTransactionService(MediaAssetRepository mediaRepository,
                                                                         AdminAccountRepository adminRepository,
                                                                         MediaReferenceChecker referenceChecker,
                                                                         Clock mediaClock) {
            return new MediaDeletionTransactionService(mediaRepository, adminRepository, referenceChecker, mediaClock);
        }
    }
}
