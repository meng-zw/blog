package com.blog.identity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    @Test
    void createsBcryptAdminWhenTableIsEmpty() throws Exception {
        AdminAccountRepository repository = mock(AdminAccountRepository.class);
        when(repository.countByEnabledTrue()).thenReturn(0L);
        when(repository.count()).thenReturn(0L);
        AdminBootstrapProperties properties = properties("owner", "bootstrap password", "小M");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        new AdminBootstrap(repository, properties, encoder, new StandardEnvironment())
                .run(new DefaultApplicationArguments());

        ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(repository).save(captor.capture());
        assertEquals("owner", captor.getValue().getUsername());
        assertEquals("小M", captor.getValue().getDisplayName());
        assertTrue(captor.getValue().isEnabled());
        assertTrue(encoder.matches("bootstrap password", captor.getValue().getPasswordHash()));
    }

    @Test
    void preservesExistingEnabledAdministrator() throws Exception {
        AdminAccountRepository repository = mock(AdminAccountRepository.class);
        when(repository.countByEnabledTrue()).thenReturn(1L);

        new AdminBootstrap(repository, properties("owner", "replacement password", "小M"),
                new BCryptPasswordEncoder(), new StandardEnvironment())
                .run(new DefaultApplicationArguments());

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void productionFailsWithoutBootstrapCredentialsWhenNoAdminExists() {
        AdminAccountRepository repository = mock(AdminAccountRepository.class);
        when(repository.countByEnabledTrue()).thenReturn(0L);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () ->
                new AdminBootstrap(repository, properties("", "", "小M"),
                        new BCryptPasswordEncoder(), environment)
                        .run(new DefaultApplicationArguments()));
    }

    private static AdminBootstrapProperties properties(String username, String password, String displayName) {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setUsername(username);
        properties.setPassword(password);
        properties.setDisplayName(displayName);
        return properties;
    }
}
