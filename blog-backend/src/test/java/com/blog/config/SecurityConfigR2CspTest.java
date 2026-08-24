package com.blog.config;

import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.media.MediaApplicationService;
import com.blog.media.MediaPurpose;
import com.blog.media.PublicMediaController;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(controllers = PublicMediaController.class)
@ContextConfiguration(classes = {PublicMediaController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "blog.media.r2.endpoint=https://account.r2.cloudflarestorage.com",
        "blog.media.r2.public-base-url=https://images.example.com/blog"
})
class SecurityConfigR2CspTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MediaApplicationService mediaApplicationService;
    @MockitoBean AdminAccountRepository adminAccountRepository;

    @Test
    void narrowlyAllowsConfiguredR2UploadAndPublicImageOrigins() throws Exception {
        when(mediaApplicationService.resolvePublic(42L)).thenReturn(new MediaApplicationService.PublicMediaAsset(
                URI.create("https://images.example.com/blog/inline-images/42.png"), "image/png", "note.png",
                MediaPurpose.INLINE_IMAGE));

        mockMvc.perform(get("/api/media/assets/42").contextPath("/api"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("connect-src 'self' https://account.r2.cloudflarestorage.com"),
                        org.hamcrest.Matchers.containsString("img-src 'self' data: https://images.example.com"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("*")))));
    }
}
