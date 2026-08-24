package com.blog.media;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.media.dto.MediaResponse;
import com.blog.media.dto.MediaUploadPlanResponse;
import com.blog.media.storage.UploadMode;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.io.InputStream;
import java.util.List;
import com.blog.media.dto.AdminMediaAssetResponse;
import com.blog.shared.web.PageResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminMediaController.class)
@ContextConfiguration(classes = {AdminMediaController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class AdminMediaControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MediaApplicationService mediaApplicationService;
    @MockitoBean AdminAccountRepository adminAccountRepository;

    @Test
    void administratorWithCsrfCanRequestSnakeCaseUploadPlan() throws Exception {
        when(mediaApplicationService.requestUpload(any(), eq("owner"))).thenReturn(new MediaUploadPlanResponse(
                42L, UploadMode.PROXY, "PUT", "/api/admin/media/uploads/42/content",
                Map.of("Content-Type", "image/png"), Instant.parse("2026-08-24T10:10:00Z")));

        mockMvc.perform(post("/api/admin/media/uploads").contextPath("/api").with(user("owner").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"note.png\",\"content_type\":\"image/png\",\"byte_size\":68,\"purpose\":\"INLINE_IMAGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media_id").value(42))
                .andExpect(jsonPath("$.upload_mode").value("PROXY"))
                .andExpect(jsonPath("$.upload_url").value("/api/admin/media/uploads/42/content"));
    }

    @Test
    void rejectsAnonymousOrCsrfLessMutationsWithProblemDetails() throws Exception {
        mockMvc.perform(post("/api/admin/media/uploads").contextPath("/api").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mockMvc.perform(put("/api/admin/media/uploads/42/content").contextPath("/api").with(user("owner").roles("ADMIN"))
                        .contentType(MediaType.IMAGE_PNG).content(new byte[]{1}))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void validatesUploadRequestBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/admin/media/uploads").contextPath("/api").with(user("owner").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"filename\":\"\",\"byte_size\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void acceptsBinaryProxyContentAndCompletesUpload() throws Exception {
        when(mediaApplicationService.complete(42L, "owner")).thenReturn(response(42L));
        mockMvc.perform(put("/api/admin/media/uploads/42/content").contextPath("/api").with(user("owner").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.IMAGE_PNG).content(new byte[]{1, 2, 3}))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/media/42/complete").contextPath("/api").with(user("owner").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/api/media/assets/42"));
        verify(mediaApplicationService).uploadProxyContent(eq(42L), eq("owner"),
                argThat(stream -> !(stream instanceof java.io.ByteArrayInputStream)));
    }

    @Test
    void deletesUnusedMediaIdempotently() throws Exception {
        mockMvc.perform(delete("/api/admin/media/42").contextPath("/api").with(user("owner").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(mediaApplicationService).delete(42L, "owner");
    }

    @Test
    void administratorCanFilterProviderNeutralMediaLibraryAndSeesReferenceState() throws Exception {
        when(mediaApplicationService.list(0, 24, MediaStatus.READY, MediaPurpose.TOOL_COVER)).thenReturn(
                new PageResponse<>(List.of(new AdminMediaAssetResponse(7L, "tool.png", "image/png", 42L,
                        1, 1, StorageProvider.R2, MediaStatus.READY, MediaPurpose.TOOL_COVER, true,
                        "/api/media/assets/7", Instant.parse("2026-08-24T00:00:00Z"))), 0, 24, 1, 1));

        mockMvc.perform(get("/api/admin/media").contextPath("/api").with(user("owner").roles("ADMIN"))
                        .param("status", "READY").param("purpose", "TOOL_COVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].provider").value("R2"))
                .andExpect(jsonPath("$.items[0].referenced").value(true))
                .andExpect(jsonPath("$.items[0].url").value("/api/media/assets/7"));
        verify(mediaApplicationService).list(0, 24, MediaStatus.READY, MediaPurpose.TOOL_COVER);
    }

    private static MediaResponse response(long id) {
        return new MediaResponse(id, "note.png", "image/png", 68, 1, 1, MediaStatus.READY,
                MediaPurpose.INLINE_IMAGE, "/api/media/assets/" + id);
    }
}
