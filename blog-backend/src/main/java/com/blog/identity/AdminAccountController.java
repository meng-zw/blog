package com.blog.identity;

import com.blog.identity.dto.ChangePasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/account")
public class AdminAccountController {
    private final AdminAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;

    public AdminAccountController(AdminAccountRepository repository, PasswordEncoder passwordEncoder,
                                  SessionRegistry sessionRegistry) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               Authentication authentication, HttpServletRequest servletRequest) {
        if (!request.newPassword().equals(request.confirmation())) {
            throw new IllegalArgumentException("新密码与确认密码不一致");
        }
        AdminAccount account = repository.findByUsernameAndEnabledTrue(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Administrator not found"));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BadCredentialsException("当前密码错误");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        repository.save(account);

        HttpSession currentSession = servletRequest.getSession(false);
        String currentSessionId = currentSession == null ? null : currentSession.getId();
        for (SessionInformation session : sessionRegistry.getAllSessions(authentication.getPrincipal(), false)) {
            if (!session.getSessionId().equals(currentSessionId)) {
                session.expireNow();
            }
        }
    }
}
