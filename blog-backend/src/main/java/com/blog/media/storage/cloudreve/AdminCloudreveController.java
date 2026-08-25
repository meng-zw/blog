package com.blog.media.storage.cloudreve;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.media.storage.cloudreve.dto.CloudreveConnectionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Administrator-only endpoints that expose Cloudreve connection metadata without credentials. */
@RestController
@RequestMapping("/admin/media/cloudreve")
public class AdminCloudreveController {
    private final CloudreveTokenService tokenService;
    private final CloudreveConnectionRepository connections;
    private final CloudreveProperties properties;
    private final AdminAccountRepository adminAccounts;
    private final Environment environment;

    public AdminCloudreveController(CloudreveTokenService tokenService, CloudreveConnectionRepository connections,
                                    CloudreveProperties properties, AdminAccountRepository adminAccounts,
                                    Environment environment) {
        this.tokenService = tokenService;
        this.connections = connections;
        this.properties = properties;
        this.adminAccounts = adminAccounts;
        this.environment = environment;
    }

    @GetMapping
    public CloudreveConnectionResponse status() {
        CloudreveConnection connection = connections.findSingleton().orElse(null);
        return new CloudreveConnectionResponse(
                isEffectivelyConfigured(),
                connection == null ? CloudreveConnectionStatus.DISCONNECTED : connection.getStatus(),
                connection == null ? null : connection.getAuthorizedSubject(),
                connection == null ? null : connection.getAuthorizedDisplayName(),
                connection == null ? List.of() : scopes(connection.getGrantedScopes()),
                connection == null ? null : connection.getAccessTokenExpiresAt(),
                connection == null ? null : connection.getRefreshTokenExpiresAt(),
                properties.getRootPath());
    }

    @PostMapping("/authorize")
    public Map<String, String> authorize(Authentication authentication, HttpServletRequest request) {
        if (!isEffectivelyConfigured()) throw new CloudreveConfigurationRequiredException();
        URI redirectUri = tokenService.beginAuthorization(adminId(authentication), existingSessionId(request));
        return Map.of("redirect_url", redirectUri.toString());
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        tokenService.disconnect(adminId(authentication));
        return ResponseEntity.noContent().build();
    }

    private long adminId(Authentication authentication) {
        AdminAccount account = adminAccounts.findByUsernameAndEnabledTrue(authentication.getName())
                .orElseThrow(CloudreveAuthorizationRequiredException::new);
        if (account.getId() == null || account.getId() <= 0) throw new CloudreveAuthorizationRequiredException();
        return account.getId();
    }

    private static String existingSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new CloudreveAuthorizationRequiredException();
        return session.getId();
    }

    private static List<String> scopes(String storedScopes) {
        if (storedScopes == null || storedScopes.isBlank()) return List.of();
        return List.of(storedScopes.trim().split("\\s+"));
    }

    private boolean isEffectivelyConfigured() {
        return CloudreveConfiguration.isEffectivelyConfigured(environment);
    }
}
