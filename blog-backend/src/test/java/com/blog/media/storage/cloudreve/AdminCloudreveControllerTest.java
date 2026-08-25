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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCloudreveController.class, properties = "blog.media.cloudreve.enabled=true")
@ContextConfiguration(classes = {AdminCloudreveController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("test")
class AdminCloudreveControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CloudreveTokenService tokenService;
    @MockitoBean CloudreveConnectionRepository connectionRepository;
    @MockitoBean CloudreveProperties properties;
    @MockitoBean AdminAccountRepository adminAccounts;

    @Test
    void administratorCanReadOnlySafeConnectionMetadata() throws Exception {
        CloudreveConnection connection = new CloudreveConnection();
        connection.setStatus(CloudreveConnectionStatus.CONNECTED);
        connection.setAuthorizedSubject("cloudreve-user-42");
        connection.setAuthorizedDisplayName("Cloudreve Owner");
        connection.setGrantedScopes("openid profile offline_access Files.Write");
        connection.setAccessTokenExpiresAt(Instant.parse("2026-08-24T10:00:00Z"));
        connection.setRefreshTokenExpiresAt(Instant.parse("2026-09-24T10:00:00Z"));
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRootPath()).thenReturn("/blog");
        when(connectionRepository.findSingleton()).thenReturn(Optional.of(connection));

        mockMvc.perform(get("/api/admin/media/cloudreve").contextPath("/api").with(user("owner").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.authorized_subject").value("cloudreve-user-42"))
                .andExpect(jsonPath("$.authorized_display_name").value("Cloudreve Owner"))
                .andExpect(jsonPath("$.granted_scopes[3]").value("Files.Write"))
                .andExpect(jsonPath("$.access_token_expires_at").value("2026-08-24T10:00:00Z"))
                .andExpect(jsonPath("$.refresh_token_expires_at").value("2026-09-24T10:00:00Z"))
                .andExpect(jsonPath("$.root_path").value("/blog"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andExpect(jsonPath("$.client_secret").doesNotExist())
                .andExpect(jsonPath("$.token_encryption_key").doesNotExist())
                .andExpect(jsonPath("$.authorization_generation").doesNotExist());
    }

    @Test
    void onlyAdministratorWithCsrfCanAuthorizeOrDisconnect() throws Exception {
        MockHttpSession session = new MockHttpSession();
        stubAdmin();
        when(properties.isEnabled()).thenReturn(true);
        when(tokenService.beginAuthorization(eq(7L), eq(session.getId())))
                .thenReturn(URI.create("https://cloudreve.example/session/authorize?state=opaque-state"));

        mockMvc.perform(post("/api/admin/media/cloudreve/authorize").contextPath("/api").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/media/cloudreve/authorize").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/media/cloudreve/disconnect").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/media/cloudreve/authorize").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_url").value("https://cloudreve.example/session/authorize?state=opaque-state"))
                .andExpect(jsonPath("$.state").doesNotExist())
                .andExpect(jsonPath("$.code_verifier").doesNotExist())
                .andExpect(jsonPath("$.client_id").doesNotExist())
                .andExpect(jsonPath("$.redirect_url").value(not(containsString("client_secret"))));
        verify(tokenService).beginAuthorization(7L, session.getId());

        mockMvc.perform(post("/api/admin/media/cloudreve/disconnect").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        verify(tokenService).disconnect(7L);
    }

    @Test
    void nonAdministratorCannotReadOrMutateConnection() throws Exception {
        mockMvc.perform(get("/api/admin/media/cloudreve").contextPath("/api").with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/media/cloudreve/disconnect").contextPath("/api")
                        .with(user("reader").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(tokenService, never()).disconnect(7L);
    }

    @Test
    void authorizationErrorsAreSanitized() throws Exception {
        MockHttpSession session = new MockHttpSession();
        stubAdmin();
        when(properties.isEnabled()).thenReturn(true);
        when(tokenService.beginAuthorization(eq(7L), eq(session.getId())))
                .thenThrow(new RuntimeException("access_token=private client_secret=private"));

        mockMvc.perform(post("/api/admin/media/cloudreve/authorize").contextPath("/api")
                        .session(session).with(user("owner").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value(not(containsString("access_token"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("client_secret"))));
    }

    private void stubAdmin() {
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        account.setUsername("owner");
        account.setEnabled(true);
        when(adminAccounts.findByUsernameAndEnabledTrue("owner")).thenReturn(Optional.of(account));
    }
}
