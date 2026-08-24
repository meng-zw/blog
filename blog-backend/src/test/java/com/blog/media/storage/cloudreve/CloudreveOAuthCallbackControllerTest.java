package com.blog.media.storage.cloudreve;

import com.blog.config.SecurityConfig;
import com.blog.identity.AdminAccount;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CloudreveOAuthCallbackController.class)
@ContextConfiguration(classes = {CloudreveOAuthCallbackController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class CloudreveOAuthCallbackControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CloudreveTokenService tokenService;
    @MockitoBean AdminAccountRepository adminAccounts;

    @Test
    void callbackRequiresOriginalAdministratorSessionAndCompletesOnlyWithItsState() throws Exception {
        MockHttpSession session = new MockHttpSession();
        stubAdmin();

        mockMvc.perform(get("/api/admin/media/cloudreve/callback").contextPath("/api")
                        .param("code", "authorization-code").param("state", "server-state"))
                .andExpect(status().isUnauthorized());
        MockHttpSession wrongSession = new MockHttpSession();
        doThrow(new CloudreveAuthorizationRequiredException()).when(tokenService)
                .completeAuthorization(eq("authorization-code"), eq("server-state"), eq(7L), eq(wrongSession.getId()));
        mockMvc.perform(get("/api/admin/media/cloudreve/callback").contextPath("/api")
                        .session(wrongSession).with(user("owner").roles("ADMIN"))
                        .param("code", "authorization-code").param("state", "server-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/settings?cloudreve=authorization_failed"));
        verify(tokenService).completeAuthorization("authorization-code", "server-state", 7L, wrongSession.getId());

        mockMvc.perform(get("/api/admin/media/cloudreve/callback").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN"))
                        .param("code", "authorization-code").param("state", "server-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/settings?cloudreve=connected"));
        verify(tokenService).completeAuthorization("authorization-code", "server-state", 7L, session.getId());
    }

    @Test
    void callbackRedirectsToFixedErrorWithoutReflectingProviderOrInternalDetails() throws Exception {
        MockHttpSession session = new MockHttpSession();
        stubAdmin();
        doThrow(new RuntimeException("error_description=private access_token=secret state=server-state"))
                .when(tokenService).completeAuthorization(eq("private-code"), eq("server-state"), eq(7L), eq(session.getId()));

        mockMvc.perform(get("/api/admin/media/cloudreve/callback").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN"))
                        .param("code", "private-code").param("state", "server-state")
                        .param("error_description", "private provider explanation"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/settings?cloudreve=authorization_failed"));
    }

    private void stubAdmin() {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("owner");
        account.setEnabled(true);
        when(adminAccounts.findByUsernameAndEnabledTrue("owner")).thenReturn(Optional.of(account));
    }
}
