package com.blog.identity;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.Serial;
import java.util.List;
import java.util.Locale;

@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final AdminAccountRepository repository;

    public AdminUserDetailsService(AdminAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        AdminAccount account = repository.findByUsernameAndEnabledTrue(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Administrator not found"));
        return new AdminPrincipal(account.getUsername(), account.getPasswordHash(), account.getDisplayName());
    }

    public static final class AdminPrincipal extends User {
        @Serial
        private static final long serialVersionUID = 1L;
        private final String displayName;

        AdminPrincipal(String username, String password, String displayName) {
            super(username, password, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
}
