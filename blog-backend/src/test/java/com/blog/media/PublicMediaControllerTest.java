package com.blog.media;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectStorageException;
import com.blog.media.storage.ObjectStorageRegistry;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@WebMvcTest(controllers = PublicMediaController.class)
@ContextConfiguration(classes = {PublicMediaController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class PublicMediaControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MediaApplicationService mediaApplicationService;
    @MockitoBean AdminAccountRepository adminAccountRepository;

    @Test
    void redirectsReadyMediaAtStableAddress() throws Exception {
        when(mediaApplicationService.resolvePublic(42L)).thenReturn(new MediaApplicationService.PublicMediaAsset(
                URI.create("https://cdn.example/inline-images/42.png"), "image/png", "note.png", MediaPurpose.INLINE_IMAGE));

        mockMvc.perform(get("/api/media/assets/42").contextPath("/api"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://cdn.example/inline-images/42.png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("immutable"))));
    }

    @Test
    void attachmentDownloadStreamsProviderContentWithSafeFinalDisposition() throws Exception {
        when(mediaApplicationService.openPublicDownload(42L)).thenReturn(new MediaApplicationService.PublicMediaContent(
                new ByteArrayInputStream("pdf-bytes".getBytes()), "application/pdf", "资料.pdf", 9L));

        mockMvc.perform(get("/api/media/assets/42/download").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().longValue("Content-Length", 9L))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().bytes("pdf-bytes".getBytes()));
    }

    @Test
    void missingLocalAttachmentReturnsSanitizedNotFound() throws Exception {
        when(mediaApplicationService.openPublicDownload(42L)).thenThrow(
                com.blog.media.storage.ObjectStorageException.notFound(
                        "Media object not found: attachments/private-object.pdf", null));

        mockMvc.perform(get("/api/media/assets/42/download").contextPath("/api"))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.detail")
                        .value("媒体文件不存在"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-object"))));
    }

    @Test
    void providerDownloadIoReturnsSanitizedServiceUnavailable() throws Exception {
        when(mediaApplicationService.openPublicDownload(42L)).thenThrow(
                new com.blog.shared.error.ServiceUnavailableException(
                        "媒体存储暂时不可用，请稍后重试", new java.io.IOException("/secret/provider/path")));

        mockMvc.perform(get("/api/media/assets/42/download").contextPath("/api"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.detail")
                        .value("媒体存储暂时不可用，请稍后重试"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret/provider"))));
    }

    @Test
    void missingInlineObjectReturnsSanitizedNotFound() throws Exception {
        ObjectStorage storage = mock(ObjectStorage.class);
        ObjectLocation location = new ObjectLocation(StorageProvider.R2, "private-bucket", "inline-images/private.png");
        when(storage.resolvePublicUrl(location)).thenThrow(
                ObjectStorageException.notFound("Missing private-bucket/inline-images/private.png", null));

        publicResolutionMockMvc(location, storage).perform(get("/media/assets/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("媒体文件不存在"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-bucket"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private.png"))));
    }

    @Test
    void missingInlineProviderAdapterReturnsSanitizedServiceUnavailable() throws Exception {
        ObjectLocation location = new ObjectLocation(StorageProvider.R2, "private-bucket", "inline-images/private.png");

        publicResolutionMockMvc(location,
                new IllegalArgumentException("No adapter for R2 private-bucket/inline-images/private.png"))
                .perform(get("/media/assets/42"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Media storage is temporarily unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-bucket"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private.png"))));
    }

    @Test
    void missingInlineBucketConfigurationReturnsSanitizedServiceUnavailable() throws Exception {
        ObjectStorage storage = mock(ObjectStorage.class);
        ObjectLocation location = new ObjectLocation(StorageProvider.R2, "private-bucket", "inline-images/private.png");
        when(storage.resolvePublicUrl(location)).thenThrow(
                new IllegalArgumentException("R2 bucket private-bucket is missing https://internal.example config"));

        publicResolutionMockMvc(location, storage).perform(get("/media/assets/42"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Media storage is temporarily unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private-bucket"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal.example"))));
    }

    private MockMvc publicResolutionMockMvc(ObjectLocation location, ObjectStorage storage) {
        ObjectStorageRegistry registry = mock(ObjectStorageRegistry.class);
        when(registry.get(location.provider())).thenReturn(storage);
        return publicResolutionMockMvc(location, registry);
    }

    private MockMvc publicResolutionMockMvc(ObjectLocation location, RuntimeException registryFailure) {
        ObjectStorageRegistry registry = mock(ObjectStorageRegistry.class);
        when(registry.get(location.provider())).thenThrow(registryFailure);
        return publicResolutionMockMvc(location, registry);
    }

    private MockMvc publicResolutionMockMvc(ObjectLocation location, ObjectStorageRegistry registry) {
        MediaAssetRepository mediaRepository = mock(MediaAssetRepository.class);
        MediaAsset asset = new MediaAsset();
        asset.setId(42L);
        asset.setProvider(location.provider());
        asset.setBucket(location.bucket());
        asset.setStorageKey(location.objectKey());
        asset.setStatus(MediaStatus.READY);
        asset.setPurpose(MediaPurpose.INLINE_IMAGE);
        asset.setOriginalFilename("safe.png");
        asset.setContentType("image/png");
        when(mediaRepository.findById(42L)).thenReturn(Optional.of(asset));
        MediaProperties properties = new MediaProperties();
        MediaApplicationService service = new MediaApplicationService(mediaRepository,
                mock(AdminAccountRepository.class), registry, new MediaContentValidator(properties),
                mock(MediaReferenceChecker.class), properties, mock(MediaDeletionService.class),
                mock(MediaDeletionTransactionService.class), mock(MediaOperationTransactionService.class),
                mock(MediaReadTransactionService.class), Clock.systemUTC());
        return standaloneSetup(new PublicMediaController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
