package com.blog.config;

import com.blog.identity.AdminUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider adminAuthenticationProvider(AdminUserDetailsService userDetailsService,
                                                                 PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider adminAuthenticationProvider) {
        return new ProviderManager(adminAuthenticationProvider);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public static HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        ConcurrentSessionControlAuthenticationStrategy concurrency =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrency.setMaximumSessions(1);
        concurrency.setExceptionIfMaximumExceeded(false);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrency,
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(Environment environment) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(production).httpOnly(false));
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CookieCsrfTokenRepository csrfTokenRepository,
                                                   SecurityContextRepository securityContextRepository,
                                                   SessionAuthenticationStrategy sessionAuthenticationStrategy,
                                                   SessionRegistry sessionRegistry,
                                                   ObjectMapper objectMapper,
                                                   Environment environment) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .sessionManagement(session -> {
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                            .sessionAuthenticationStrategy(sessionAuthenticationStrategy);
                    session.maximumSessions(1)
                            .sessionRegistry(sessionRegistry)
                            .expiredSessionStrategy(event -> writeProblem(event.getRequest(), event.getResponse(),
                                    objectMapper, HttpStatus.UNAUTHORIZED, "Unauthorized", "会话已失效"));
                })
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/public/**", "/sitemap.xml", "/media/**",
                                "/actuator/health", "/actuator/health/**", "/admin/session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin/media/cloudreve/callback").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                request, response, objectMapper, HttpStatus.UNAUTHORIZED,
                                "Unauthorized", "需要管理员登录"))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                request, response, objectMapper, HttpStatus.FORBIDDEN,
                                "Forbidden", "请求被拒绝")))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                contentSecurityPolicy(environment)))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .contentTypeOptions(Customizer.withDefaults()))
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    static String contentSecurityPolicy(Environment environment) {
        java.util.LinkedHashSet<String> uploadOrigins = new java.util.LinkedHashSet<>();
        String endpoint = environment.getProperty("blog.media.r2.endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            String accountId = environment.getProperty("blog.media.r2.account-id");
            if (accountId != null && !accountId.isBlank()) {
                endpoint = "https://" + accountId.strip() + ".r2.cloudflarestorage.com";
            }
        }
        addOrigin(uploadOrigins, endpoint, "R2 upload endpoint");

        java.util.LinkedHashSet<String> imageOrigins = new java.util.LinkedHashSet<>();
        addOrigin(imageOrigins, environment.getProperty("blog.media.r2.public-base-url"), "R2 public base URL");
        String legacyBuckets = environment.getProperty("blog.media.r2.legacy-buckets");
        if (legacyBuckets != null && !legacyBuckets.isBlank()) {
            for (String entry : legacyBuckets.split(",")) {
                int separator = entry.indexOf('=');
                if (separator > 0 && separator < entry.length() - 1) {
                    addOrigin(imageOrigins, entry.substring(separator + 1), "R2 legacy public base URL");
                }
            }
        }
        String connectSources = uploadOrigins.isEmpty() ? "" : " " + String.join(" ", uploadOrigins);
        String imageSources = imageOrigins.isEmpty() ? "" : " " + String.join(" ", imageOrigins);
        return "default-src 'self'; img-src 'self' data:" + imageSources
                + "; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'" + connectSources
                + "; object-src 'none'; base-uri 'self'; frame-ancestors 'none'";
    }

    private static void addOrigin(java.util.Set<String> destinations, String configuredUrl, String name) {
        if (configuredUrl == null || configuredUrl.isBlank()) return;
        URI uri;
        try {
            uri = URI.create(configuredUrl.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be an absolute HTTPS URL", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(name + " must be an absolute HTTPS URL without credentials");
        }
        String origin = "https://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        destinations.add(origin);
    }

    private static void writeProblem(HttpServletRequest request, HttpServletResponse response,
                                     ObjectMapper objectMapper, HttpStatus status,
                                     String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = response.getHeader("X-Trace-Id");
        }
        problem.setProperty("traceId", traceId);
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
