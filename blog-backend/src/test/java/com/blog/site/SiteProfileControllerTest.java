package com.blog.site;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.media.MediaAssetRepository;
import com.blog.media.MediaAsset;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PublicSiteController.class, AdminSiteController.class})
@ContextConfiguration(classes = {PublicSiteController.class, AdminSiteController.class,
        SiteProfileService.class, SecurityConfig.class, AdminUserDetailsService.class, LoginAttemptService.class,
        TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class SiteProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SiteProfileRepository siteProfileRepository;

    @MockitoBean
    private MediaAssetRepository mediaAssetRepository;

    @MockitoBean
    private AdminAccountRepository adminAccountRepository;

    private SiteProfile seededProfile;

    @BeforeEach
    void seedProfile() {
        seededProfile = new SiteProfile();
        seededProfile.setSiteName("小M的思与行");
        seededProfile.setSubtitle("中庸之道");
        seededProfile.setSiteDescription("中庸之道");
        seededProfile.setOwnerName("小M");
        seededProfile.setGithubUrl("https://github.com/meng-zw");
        when(siteProfileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(seededProfile));
        when(siteProfileRepository.save(any(SiteProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publicProfileReturnsApprovedIdentityDefaults() throws Exception {
        mockMvc.perform(get("/api/public/site-profile").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.site_title").value("小M的思与行"))
                .andExpect(jsonPath("$.subtitle").value("中庸之道"))
                .andExpect(jsonPath("$.nickname").value("小M"))
                .andExpect(jsonPath("$.bio").value("中庸之道"))
                .andExpect(jsonPath("$.github_url").value("https://github.com/meng-zw"))
                .andExpect(jsonPath("$.avatar_url").value("/images/xiao-m-mark.png"))
                .andExpect(jsonPath("$.avatar_media_id").doesNotExist());
    }

    @Test
    void unauthenticatedProfileUpdateReturnsUnauthorizedProblem() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .contextPath("/api")
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void textOnlyAdminRoundTripPreservesExistingAvatarMediaId() throws Exception {
        MediaAsset avatar = new MediaAsset();
        avatar.setId(42L);
        avatar.setStorageKey("existing-avatar.png");
        seededProfile.setAvatarMedia(avatar);
        when(mediaAssetRepository.findById(42L)).thenReturn(Optional.of(avatar));

        mockMvc.perform(get("/api/admin/settings").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_media_id").value(42))
                .andExpect(jsonPath("$.avatar_url").value("/api/media/existing-avatar.png"));

        mockMvc.perform(put("/api/admin/settings")
                        .contextPath("/api")
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest().replace("中庸之道", "且听风吟")
                                .replace("\"avatar_media_id\":null", "\"avatar_media_id\":42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtitle").value("且听风吟"))
                .andExpect(jsonPath("$.avatar_media_id").value(42))
                .andExpect(jsonPath("$.avatar_url").value("/api/media/existing-avatar.png"));

        verify(mediaAssetRepository).findById(42L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void profileUpdateRejectsNonHttpsGithubUrl() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .contextPath("/api")
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest().replace("https://github.com/meng-zw", "http://github.com/meng-zw")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.githubUrl").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void profileUpdateRejectsLookalikeGithubHost() throws Exception {
        mockMvc.perform(put("/api/admin/settings")
                        .contextPath("/api")
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRequest().replace("https://github.com/meng-zw", "https://github.com.evil.example/meng-zw")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.githubUrl").exists());
    }

    private static String validRequest() {
        return """
                {"site_title":"小M的思与行","subtitle":"中庸之道","nickname":"小M",
                 "bio":"中庸之道","github_url":"https://github.com/meng-zw","avatar_media_id":null}
                """;
    }
}
