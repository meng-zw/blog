package com.blog.media;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@ContextConfiguration(classes = {MediaController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaStorageService mediaStorageService;

    @MockitoBean
    private AdminAccountRepository adminAccountRepository;

    @TempDir
    Path mediaDirectory;

    @Test
    void legacyAdministrativeUploadRouteHasBeenRemoved() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file("file", "not an image".getBytes())
                        .contextPath("/api")
                        .with(user("owner").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void anonymousUploadReturnsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file("file", "not an image".getBytes())
                        .contextPath("/api")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void authenticatedUploadWithoutCsrfReturnsForbidden() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file("file", "not an image".getBytes())
                        .contextPath("/api")
                        .with(user("owner").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void publicMediaGetUsesSafeResponseHeadersWithoutLeakingLocalPath() throws Exception {
        String storageKey = "123e4567-e89b-12d3-a456-426614174000.png";
        Path source = mediaDirectory.resolve("private-source.png");
        Files.write(source, new byte[]{1, 2, 3});
        MediaAsset asset = new MediaAsset();
        asset.setStorageKey(storageKey);
        asset.setContentType("image/png");
        when(mediaStorageService.findByStorageKey(storageKey)).thenReturn(asset);
        when(mediaStorageService.load(storageKey)).thenReturn(source);

        mockMvc.perform(get("/api/media/" + storageKey).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        verify(mediaStorageService).findByStorageKey(storageKey);
        verify(mediaStorageService).load(storageKey);
    }

    @Test
    void legacyRouteReadsPurposePrefixedLocalKeys() throws Exception {
        String storageKey = "inline-images/123e4567-e89b-12d3-a456-426614174000.png";
        Path source = mediaDirectory.resolve("private-source.png");
        Files.write(source, new byte[]{1, 2, 3});
        MediaAsset asset = new MediaAsset();
        asset.setStorageKey(storageKey);
        asset.setContentType("image/png");
        when(mediaStorageService.findByStorageKey(storageKey)).thenReturn(asset);
        when(mediaStorageService.load(storageKey)).thenReturn(source);

        mockMvc.perform(get("/api/media/" + storageKey).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
