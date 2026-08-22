package com.blog.identity;

import com.blog.identity.AdminUserDetailsService.AdminPrincipal;
import com.blog.identity.dto.LoginRequest;
import com.blog.identity.dto.SessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/admin/session")
public class AdminSessionController {
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SessionRegistry sessionRegistry;

    public AdminSessionController(AuthenticationManager authenticationManager,
                                  LoginAttemptService loginAttemptService,
                                  SessionAuthenticationStrategy sessionAuthenticationStrategy,
                                  SecurityContextRepository securityContextRepository,
                                  SessionRegistry sessionRegistry) {
        this.authenticationManager = authenticationManager;
        this.loginAttemptService = loginAttemptService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping
    public SessionResponse session(Authentication authentication, CsrfToken csrfToken) {
        csrfToken.getToken();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SessionResponse.anonymous();
        }
        return responseFor(authentication);
    }

    @PostMapping
    public SessionResponse login(@Valid @RequestBody LoginRequest request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse servletResponse) {
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        String clientIp = servletRequest.getRemoteAddr();
        if (loginAttemptService.isBlocked(username, clientIp)) {
            throw new BadCredentialsException("Login temporarily blocked");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(username, clientIp);
            throw new BadCredentialsException("Invalid credentials", exception);
        }
        loginAttemptService.recordSuccess(username, clientIp);
        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return responseFor(authentication);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionRegistry.removeSessionInformation(session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private static SessionResponse responseFor(Authentication authentication) {
        String displayName = authentication.getPrincipal() instanceof AdminPrincipal principal
                ? principal.getDisplayName() : authentication.getName();
        return new SessionResponse(true, authentication.getName(), displayName);
    }
}
