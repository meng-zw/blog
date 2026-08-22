package com.blog.identity;

import com.blog.config.SecurityConfig;
import com.blog.shared.error.GlobalExceptionHandler;
import com.blog.shared.web.TraceIdFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminSessionController.class, AdminAccountController.class, PublicPingController.class})
@ContextConfiguration(classes = {AdminSessionController.class, AdminAccountController.class,
        PublicPingController.class, SecurityConfig.class, AdminUserDetailsService.class,
        LoginAttemptService.class, TraceIdFilter.class, GlobalExceptionHandler.class})
@ImportAutoConfiguration(ServletWebServerFactoryAutoConfiguration.class)
@ActiveProfiles("prod")
class AdminSessionControllerTest {

    private static final String USERNAME = "owner";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private CookieCsrfTokenRepository csrfTokenRepository;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @MockitoBean
    private AdminAccountRepository repository;

    @BeforeEach
    void configureAdmin() {
        AdminAccount account = new AdminAccount();
        account.setId(1L);
        account.setUsername(USERNAME);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setDisplayName("小M");
        account.setEnabled(true);
        when(repository.findByUsernameAndEnabledTrue(anyString())).thenAnswer(invocation ->
                USERNAME.equals(invocation.getArgument(0)) ? Optional.of(account) : Optional.empty());
    }

    @AfterEach
    void restoreCookieCsrfRepository() {
        CsrfFilter csrfFilter = filterChainProxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(CsrfFilter.class::isInstance)
                .map(CsrfFilter.class::cast)
                .findFirst()
                .orElseThrow();
        ReflectionTestUtils.setField(csrfFilter, "tokenRepository", csrfTokenRepository);
    }

    @Test
    void permitsPublicPing() throws Exception {
        mockMvc.perform(apiGet("/public/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousSessionResponseCreatesReadableCsrfCookie() throws Exception {
        mockMvc.perform(apiGet("/admin/session"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void unauthenticatedAdminRequestWithCsrfReturnsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/admin/articles").contextPath("/api").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void loginCreatesSessionAndRotatesExistingSessionId() throws Exception {
        MockHttpSession originalSession = new MockHttpSession();
        String originalId = originalSession.getId();

        MvcResult login = mockMvc.perform(post("/api/admin/session")
                        .contextPath("/api")
                        .session(originalSession)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"owner\",\"password\":\"correct horse battery staple\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.display_name").value("小M"))
                .andReturn();

        assertNotEquals(originalId, login.getRequest().getSession(false).getId());
    }

    @Test
    void loginWithoutCsrfReturnsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/admin/session")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content("{\"username\":\"owner\",\"password\":\"correct horse battery staple\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void loginAcceptsCsrfCookieValueInXsrfHeader() throws Exception {
        Cookie csrfCookie = mockMvc.perform(apiGet("/admin/session"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/admin/session")
                        .contextPath("/api")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType("application/json")
                        .content("{\"username\":\"owner\",\"password\":\"correct horse battery staple\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        HttpSession session = login(new MockHttpSession());

        mockMvc.perform(delete("/api/admin/session")
                        .contextPath("/api")
                        .session((MockHttpSession) session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThrows(IllegalStateException.class, session::getCreationTime);
    }

    @Test
    void bearerTokenCannotAuthenticateAdminRequest() throws Exception {
        mockMvc.perform(post("/api/admin/articles")
                        .contextPath("/api")
                        .header("Authorization", "Bearer legacy-token")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendsRequiredSecurityHeaders() throws Exception {
        mockMvc.perform(apiGet("/admin/session"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Referrer-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void secondLoginExpiresTheFirstAdminSession() throws Exception {
        HttpSession firstSession = login(new MockHttpSession());

        login(new MockHttpSession());

        mockMvc.perform(get("/api/admin/session")
                        .contextPath("/api")
                        .session((MockHttpSession) firstSession))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void passwordChangeRetainsCurrentSessionAndInvalidatesOtherSessions() throws Exception {
        HttpSession currentSession = login(new MockHttpSession());
        SecurityContext context = (SecurityContext) currentSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        sessionRegistry.registerNewSession("other-session", context.getAuthentication().getPrincipal());

        mockMvc.perform(put("/api/admin/account/password")
                        .contextPath("/api")
                        .session((MockHttpSession) currentSession)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"current_password\":\"correct horse battery staple\","
                                + "\"new_password\":\"a new secure password\","
                                + "\"confirmation\":\"a new secure password\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/session")
                        .contextPath("/api")
                        .session((MockHttpSession) currentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
        assertTrue(sessionRegistry.getSessionInformation("other-session").isExpired());
    }

    @Test
    void forwardedClientIpsUseIndependentFailureBuckets() throws Exception {
        String firstIp = "198.51.100.10";
        String secondIp = "203.0.113.20";

        for (int attempt = 0; attempt < 5; attempt++) {
            failedLogin(USERNAME, "wrong password", firstIp)
                    .andExpect(status().isUnauthorized());
        }

        assertTrue(loginAttemptService.isBlocked(USERNAME, firstIp));
        assertFalse(loginAttemptService.isBlocked(USERNAME, secondIp));

        for (int attempt = 0; attempt < 5; attempt++) {
            failedLogin(USERNAME, "wrong password", secondIp)
                    .andExpect(status().isUnauthorized());
        }

        assertTrue(loginAttemptService.isBlocked(USERNAME, secondIp));
    }

    @Test
    void oversizedUsernameReturnsProblemWithoutAddingThrottleEntry() throws Exception {
        String oversizedUsername = "u".repeat(101);
        String clientIp = "198.51.100.31";

        for (int attempt = 0; attempt < 5; attempt++) {
            failedLogin(oversizedUsername, PASSWORD, clientIp)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        assertFalse(loginAttemptService.isBlocked(oversizedUsername, clientIp));
    }

    @Test
    void oversizedPasswordReturnsProblemWithoutAddingThrottleEntry() throws Exception {
        String oversizedPassword = "p".repeat(73);
        String clientIp = "198.51.100.32";

        for (int attempt = 0; attempt < 5; attempt++) {
            failedLogin(USERNAME, oversizedPassword, clientIp)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }

        assertFalse(loginAttemptService.isBlocked(USERNAME, clientIp));
    }

    private HttpSession login(MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/admin/session")
                        .contextPath("/api")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"owner\",\"password\":\"correct horse battery staple\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions failedLogin(
            String username, String password, String forwardedIp) throws Exception {
        return mockMvc.perform(post("/api/admin/session")
                .contextPath("/api")
                .remoteAddress("172.20.0.10")
                .header("X-Forwarded-For", forwardedIp)
                .header("X-Forwarded-Proto", "https")
                .with(csrf())
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder apiGet(String path) {
        return get("/api" + path).contextPath("/api");
    }
}
