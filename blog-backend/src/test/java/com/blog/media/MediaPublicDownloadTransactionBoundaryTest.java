package com.blog.media;

import com.blog.identity.AdminAccountRepository;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectStorageRegistry;
import com.blog.media.storage.r2.R2ObjectStorage;
import com.blog.media.storage.r2.R2Properties;
import com.blog.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@SpringJUnitConfig(MediaPublicDownloadTransactionBoundaryTest.Config.class)
class MediaPublicDownloadTransactionBoundaryTest {

    @jakarta.annotation.Resource MediaApplicationService applicationService;
    @jakarta.annotation.Resource MediaAssetRepository repository;
    @jakarta.annotation.Resource S3Client r2Client;

    @BeforeEach
    void resetProviderAndRepository() throws Exception {
        reset(repository, r2Client);
        stubReadyAsset("blog-media", MediaPurpose.INLINE_IMAGE);
        when(r2Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return HeadObjectResponse.builder().contentType("image/png").contentLength(7L).eTag("etag-1").build();
        });
        when(r2Client.getObject(any(GetObjectRequest.class), org.mockito.ArgumentMatchers
                .<ResponseTransformer<GetObjectResponse, ResponseInputStream<GetObjectResponse>>>any()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                            AbortableInputStream.create(new ByteArrayInputStream("content".getBytes())));
                });
    }

    @Test
    void redirectsOnlyAfterSnapshotTransactionAndAuthoritativeR2HeadHaveEnded() throws Exception {
        publicMockMvc().perform(get("/media/assets/42"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://images.example.com/blog/inline-images/file.png"));
        verify(r2Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void missingR2ObjectReturnsSanitizedNotFoundFromTheRealAdapter() throws Exception {
        when(r2Client.headObject(any(HeadObjectRequest.class))).thenThrow(
                NoSuchKeyException.builder().message("private-bucket/inline-images/private.png").build());

        publicMockMvc().perform(get("/media/assets/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("媒体文件不存在"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-bucket"))))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void transientR2HeadFailureReturnsSanitizedServiceUnavailableFromTheRealAdapter() throws Exception {
        when(r2Client.headObject(any(HeadObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(503).message("internal provider outage").build());

        publicMockMvc().perform(get("/media/assets/42"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("媒体存储暂时不可用，请稍后重试"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal provider"))))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void unconfiguredPersistedR2BucketReturnsSanitizedServiceUnavailableOutsideTheSnapshotTransaction() throws Exception {
        reset(repository);
        stubReadyAsset("private-archive", MediaPurpose.INLINE_IMAGE);

        publicMockMvc().perform(get("/media/assets/42"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Media storage is temporarily unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-archive"))))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void opensProviderStreamOnlyAfterReadySnapshotTransactionHasEnded() throws Exception {
        reset(repository);
        stubReadyAsset("blog-media", MediaPurpose.ATTACHMENT);

        var media = applicationService.openPublicDownload(42L);

        assertThat(media.content().readAllBytes()).isEqualTo("content".getBytes());
        media.content().close();
    }

    private void stubReadyAsset(String bucket, MediaPurpose purpose) {
        when(repository.findById(42L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            MediaAsset asset = new MediaAsset();
            asset.setId(42L);
            asset.setProvider(StorageProvider.R2);
            asset.setBucket(bucket);
            asset.setStorageKey(purpose == MediaPurpose.ATTACHMENT
                    ? "attachments/file.pdf" : "inline-images/file.png");
            asset.setStatus(MediaStatus.READY);
            asset.setPurpose(purpose);
            asset.setOriginalFilename(purpose == MediaPurpose.ATTACHMENT ? "file.pdf" : "file.png");
            asset.setContentType(purpose == MediaPurpose.ATTACHMENT ? "application/pdf" : "image/png");
            asset.setByteSize(7L);
            return Optional.of(asset);
        });
    }

    private org.springframework.test.web.servlet.MockMvc publicMockMvc() {
        return standaloneSetup(new PublicMediaController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
            return mock(MediaAssetRepository.class);
        }

        @Bean
        MediaReadTransactionService mediaReadTransactionService(MediaAssetRepository repository) {
            return new MediaReadTransactionService(repository);
        }

        @Bean
        S3Client r2Client() {
            return mock(S3Client.class);
        }

        @Bean
        ObjectStorage objectStorage(S3Client client) {
            R2Properties properties = new R2Properties();
            properties.setAccountId("test-account");
            properties.setAccessKeyId("test-access-key");
            properties.setSecretAccessKey("test-secret-key");
            properties.setBucket("blog-media");
            properties.setEndpoint("https://test-account.r2.cloudflarestorage.com");
            properties.setPublicBaseUrl("https://images.example.com/blog");
            return new R2ObjectStorage(client, mock(S3Presigner.class), properties);
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
