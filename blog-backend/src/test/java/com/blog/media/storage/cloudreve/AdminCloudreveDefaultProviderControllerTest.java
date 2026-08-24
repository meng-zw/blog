package com.blog.media.storage.cloudreve;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCloudreveController.class, properties = "blog.media.provider=cloudreve")
@ContextConfiguration(classes = {AdminCloudreveController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class AdminCloudreveDefaultProviderControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CloudreveTokenService tokenService;
    @MockitoBean CloudreveConnectionRepository connectionRepository;
    @MockitoBean CloudreveProperties properties;
    @MockitoBean AdminAccountRepository adminAccounts;

    @Test
    void reportsConfiguredWhenCloudreveIsTheDefaultProviderEvenWithReadsDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/media/cloudreve").contextPath("/api").with(user("owner").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }
}
