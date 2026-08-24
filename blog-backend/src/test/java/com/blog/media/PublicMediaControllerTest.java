package com.blog.media;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
