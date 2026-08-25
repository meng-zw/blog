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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCloudreveController.class, properties = {
        "blog.media.provider=local", "blog.media.cloudreve.enabled=false"
})
@ContextConfiguration(classes = {AdminCloudreveController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class AdminCloudreveDisabledControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CloudreveTokenService tokenService;
    @MockitoBean CloudreveConnectionRepository connectionRepository;
    @MockitoBean CloudreveProperties properties;
    @MockitoBean AdminAccountRepository adminAccounts;

    @Test
    void rejectsAuthorizationWithoutLeakingMissingConfiguration() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/admin/media/cloudreve/authorize").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(not(containsString("client"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("base"))));
        verify(tokenService, never()).beginAuthorization(eq(7L), eq(session.getId()));
    }
}
