package com.blog.site;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccountRepository;
import com.blog.identity.AdminUserDetailsService;
import com.blog.identity.LoginAttemptService;
import com.blog.media.MediaAssetRepository;
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

    @BeforeEach
    void seedProfile() {
        SiteProfile profile = new SiteProfile();
        profile.setSiteName("小M的思与行");
        profile.setSubtitle("中庸之道");
        profile.setSiteDescription("中庸之道");
        profile.setOwnerName("小M");
        profile.setGithubUrl("https://github.com/meng-zw");
        when(siteProfileRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(profile));
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
                .andExpect(jsonPath("$.avatar_url").value("/images/xiao-m-mark.png"));
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

    private static String validRequest() {
        return """
                {"site_title":"小M的思与行","subtitle":"中庸之道","nickname":"小M",
                 "bio":"中庸之道","github_url":"https://github.com/meng-zw","avatar_media_id":null}
                """;
    }
}
