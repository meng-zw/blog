package com.blog.identity;

import com.blog.config.SecurityConfig;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSessionController.class)
@ContextConfiguration(classes = {AdminSessionController.class, SecurityConfig.class,
        AdminUserDetailsService.class, LoginAttemptService.class, TraceIdFilter.class,
        GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
class AdminClientIpTrustTest {

    private static final String USERNAME = "direct-owner";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @MockitoBean
    private AdminAccountRepository repository;

    @BeforeEach
    void configureAdmin() {
        AdminAccount account = new AdminAccount();
        account.setUsername(USERNAME);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setDisplayName("小M");
        account.setEnabled(true);
        when(repository.findByUsernameAndEnabledTrue(anyString())).thenAnswer(invocation ->
                USERNAME.equals(invocation.getArgument(0)) ? Optional.of(account) : Optional.empty());
    }

    @Test
    void directDeploymentIgnoresUntrustedForwardedForHeader() throws Exception {
        String directPeerIp = "192.0.2.44";
        String spoofedIp = "198.51.100.99";

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/admin/session")
                            .contextPath("/api")
                            .remoteAddress(directPeerIp)
                            .header("X-Forwarded-For", spoofedIp)
                            .with(csrf())
                            .contentType("application/json")
                            .content("{\"username\":\"direct-owner\",\"password\":\"wrong password\"}"))
                    .andExpect(status().isUnauthorized());
        }

        assertTrue(loginAttemptService.isBlocked(USERNAME, directPeerIp));
        assertFalse(loginAttemptService.isBlocked(USERNAME, spoofedIp));
    }
}
