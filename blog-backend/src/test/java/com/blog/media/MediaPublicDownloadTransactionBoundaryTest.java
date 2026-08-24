package com.blog.media;

import com.blog.identity.AdminAccountRepository;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectStorageRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(MediaPublicDownloadTransactionBoundaryTest.Config.class)
class MediaPublicDownloadTransactionBoundaryTest {

    @jakarta.annotation.Resource MediaApplicationService applicationService;

    @Test
    void opensProviderStreamOnlyAfterReadySnapshotTransactionHasEnded() throws Exception {
        var content = applicationService.openPublicDownload(42L);

        assertThat(content.content().readAllBytes()).isEqualTo("content".getBytes());
        content.content().close();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean
        org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        MediaAssetRepository mediaAssetRepository() {
            MediaAssetRepository repository = mock(MediaAssetRepository.class);
            MediaAsset asset = new MediaAsset();
            asset.setId(42L); asset.setProvider(StorageProvider.LOCAL); asset.setBucket("");
            asset.setStorageKey("attachments/file.pdf"); asset.setStatus(MediaStatus.READY);
            asset.setPurpose(MediaPurpose.ATTACHMENT); asset.setOriginalFilename("file.pdf");
            asset.setContentType("application/pdf"); asset.setByteSize(7L);
            when(repository.findById(42L)).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                return Optional.of(asset);
            });
            return repository;
        }

        @Bean
        MediaReadTransactionService mediaReadTransactionService(MediaAssetRepository repository) {
            return new MediaReadTransactionService(repository);
        }

        @Bean
        ObjectStorage objectStorage() throws Exception {
            ObjectStorage storage = mock(ObjectStorage.class);
            when(storage.provider()).thenReturn(StorageProvider.LOCAL);
            when(storage.openStream(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return new ByteArrayInputStream("content".getBytes());
            });
            return storage;
        }

        @Bean
        ObjectStorageRegistry objectStorageRegistry(ObjectStorage storage) {
            return new ObjectStorageRegistry(java.util.List.of(storage));
        }

        @Bean
        MediaApplicationService mediaApplicationService(MediaAssetRepository repository,
                                                        ObjectStorageRegistry registry,
                                                        MediaReadTransactionService reads) {
            MediaProperties properties = new MediaProperties();
            return new MediaApplicationService(repository, mock(AdminAccountRepository.class), registry,
                    new MediaContentValidator(properties), mock(MediaReferenceChecker.class), properties,
                    mock(MediaDeletionService.class), mock(MediaDeletionTransactionService.class),
                    mock(MediaOperationTransactionService.class), reads,
                    Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC));
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
