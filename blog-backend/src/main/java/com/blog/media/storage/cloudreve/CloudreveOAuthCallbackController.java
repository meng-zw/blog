package com.blog.media.storage.cloudreve;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OAuth callback that consumes an authorization transaction bound to the initiating administrator session. */
@RestController
@RequestMapping("/admin/media/cloudreve/callback")
public class CloudreveOAuthCallbackController {
    private static final String CONNECTED_REDIRECT = "/admin/settings?cloudreve=connected";
    private static final String AUTHORIZATION_FAILED_REDIRECT = "/admin/settings?cloudreve=authorization_failed";

    private final CloudreveTokenService tokenService;
    private final AdminAccountRepository adminAccounts;

    public CloudreveOAuthCallbackController(CloudreveTokenService tokenService, AdminAccountRepository adminAccounts) {
        this.tokenService = tokenService;
        this.adminAccounts = adminAccounts;
    }

    @GetMapping
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         Authentication authentication, HttpServletRequest request) {
        try {
            tokenService.completeAuthorization(code, state, adminId(authentication), existingSessionId(request));
            return redirect(CONNECTED_REDIRECT);
        } catch (RuntimeException ignored) {
            // OAuth error values and provider failures are deliberately never reflected to the browser.
            return redirect(AUTHORIZATION_FAILED_REDIRECT);
        }
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

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
