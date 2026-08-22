package com.blog.identity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AdminAccountRepository repository;
    private final AdminBootstrapProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminBootstrap(AdminAccountRepository repository, AdminBootstrapProperties properties,
                          PasswordEncoder passwordEncoder, Environment environment) {
        this.repository = repository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.countByEnabledTrue() > 0) {
            return;
        }
        String username = normalized(properties.getUsername());
        String password = properties.getPassword() == null ? "" : properties.getPassword();
        if (username.isBlank() || password.isBlank()) {
            if (isProduction()) {
                throw new IllegalStateException(
                        "Production requires blog.admin.bootstrap.username and blog.admin.bootstrap.password when no administrator exists");
            }
            return;
        }
        if (repository.count() != 0) {
            if (isProduction()) {
                throw new IllegalStateException("No enabled administrator exists and the admin_account table is not empty");
            }
            return;
        }
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setDisplayName(properties.getDisplayName() == null || properties.getDisplayName().isBlank()
                ? "小M" : properties.getDisplayName().strip());
        account.setEnabled(true);
        repository.save(account);
    }

    private boolean isProduction() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private static String normalized(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
