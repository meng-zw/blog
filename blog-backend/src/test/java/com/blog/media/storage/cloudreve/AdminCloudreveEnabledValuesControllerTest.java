package com.blog.media.storage.cloudreve;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminCloudreveEnabledValuesControllerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CloudreveConfiguration.class, AdminCloudreveController.class)
            .withBean(CloudreveTokenService.class, () -> mock(CloudreveTokenService.class))
            .withBean(CloudreveConnectionRepository.class, () -> mock(CloudreveConnectionRepository.class))
            .withBean(AdminAccountRepository.class, () -> mock(AdminAccountRepository.class))
            .withPropertyValues(
                    "blog.media.provider=local",
                    "blog.media.cloudreve.base-url=https://cloudreve.example",
                    "blog.media.cloudreve.redirect-uri=https://blog.example/api/admin/media/cloudreve/callback",
                    "blog.media.cloudreve.client-id=client-id",
                    "blog.media.cloudreve.client-secret=client-secret",
                    "blog.media.cloudreve.policy-id=policy-example",
                    "blog.media.cloudreve.token-encryption-key=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");

    @ParameterizedTest
    @ValueSource(strings = {"on", "yes", "1"})
    void statusAndAuthorizeUseTheSameSpringBooleanEnabledValuesAsStartup(String enabled) {
        contextRunner.withPropertyValues("blog.media.cloudreve.enabled=" + enabled).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CloudreveConfiguration.CloudreveStartupValidator.class);

            CloudreveTokenService tokens = context.getBean(CloudreveTokenService.class);
            CloudreveConnectionRepository connections = context.getBean(CloudreveConnectionRepository.class);
            AdminAccountRepository accounts = context.getBean(AdminAccountRepository.class);
            AdminCloudreveController controller = context.getBean(AdminCloudreveController.class);
            AdminAccount account = new AdminAccount();
            account.setId(7L);
            account.setUsername("owner");
            account.setEnabled(true);
            TestingAuthenticationToken authentication =
                    new TestingAuthenticationToken("owner", "ignored", "ROLE_ADMIN");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setSession(new MockHttpSession());
            when(connections.findSingleton()).thenReturn(Optional.empty());
            when(accounts.findByUsernameAndEnabledTrue("owner")).thenReturn(Optional.of(account));
            when(tokens.beginAuthorization(7L, request.getSession(false).getId()))
                    .thenReturn(URI.create("https://cloudreve.example/authorize"));

            assertThat(controller.status().configured()).isTrue();
            assertThat(controller.authorize(authentication, request))
                    .containsEntry("redirect_url", "https://cloudreve.example/authorize");
        });
    }
}
