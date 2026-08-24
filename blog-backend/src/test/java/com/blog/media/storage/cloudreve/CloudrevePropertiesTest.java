package com.blog.media.storage.cloudreve;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudrevePropertiesTest {

    @Test
    void derivesOAuthEndpointsFromTheConfiguredBaseUrl() {
        CloudreveProperties properties = configuredProperties();

        assertThat(properties.authorizationUri()).isEqualTo(URI.create("https://cloud.example/session/authorize"));
        assertThat(properties.tokenUri()).isEqualTo(URI.create("https://cloud.example/api/v4/session/oauth/token"));
        assertThat(properties.refreshUri()).isEqualTo(URI.create("https://cloud.example/api/v4/session/token/refresh"));
        assertThat(properties.userInfoUri()).isEqualTo(URI.create("https://cloud.example/api/v4/session/oauth/userinfo"));
    }

    @Test
    void usesExplicitOAuthEndpointOverrides() {
        CloudreveProperties properties = configuredProperties();
        properties.setAuthorizationUri(URI.create("https://identity.example:9443/authorize"));
        properties.setTokenUri(URI.create("https://identity.example:9443/token"));
        properties.setRefreshUri(URI.create("https://identity.example:9443/refresh"));
        properties.setUserInfoUri(URI.create("https://identity.example:9443/userinfo"));

        assertThat(properties.authorizationUri()).isEqualTo(URI.create("https://identity.example:9443/authorize"));
        assertThat(properties.tokenUri()).isEqualTo(URI.create("https://identity.example:9443/token"));
        assertThat(properties.refreshUri()).isEqualTo(URI.create("https://identity.example:9443/refresh"));
        assertThat(properties.userInfoUri()).isEqualTo(URI.create("https://identity.example:9443/userinfo"));
    }

    @Test
    void permitsTrustedInternalHttpOnlyWhenExplicitlyEnabled() {
        CloudreveProperties properties = configuredProperties();
        properties.setBaseUrl(URI.create("http://cloudreve.internal:5212"));
        properties.setRedirectUri(URI.create("http://blog.internal:8081/api/admin/media/cloudreve/callback"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");

        properties.setAllowTrustedInternalHttp(true);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeOAuthUris() {
        CloudreveProperties properties = configuredProperties();
        properties.setTokenUri(URI.create("https://client:credential@cloud.example/token"));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentials");
    }

    @Test
    void normalizesTheCloudreveRootPath() {
        CloudreveProperties properties = configuredProperties();
        properties.setRootPath(" //blog//images/ ");

        assertThat(properties.getRootPath()).isEqualTo("/blog/images");
    }

    @Test
    void rejectsUnsafeCloudreveRootPaths() {
        CloudreveProperties properties = configuredProperties();
        properties.setRootPath("/blog/../private");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root path");
    }

    @Test
    void doesNotRequireCloudreveSecretsWhenLocalIsTheDefaultAndCloudreveIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(CloudreveConfiguration.class)
                .withPropertyValues("blog.media.provider=local")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void requiresCompleteCloudreveConfigurationWhenCloudreveIsEnabledForReading() {
        new ApplicationContextRunner()
                .withUserConfiguration(CloudreveConfiguration.class)
                .withPropertyValues("blog.media.provider=local", "blog.media.cloudreve.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void keepsCloudreveReadableWhenLocalRemainsTheDefaultUploadProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(CloudreveConfiguration.class)
                .withPropertyValues(
                        "blog.media.provider=local",
                        "blog.media.cloudreve.enabled=true",
                        "blog.media.cloudreve.base-url=https://cloud.example",
                        "blog.media.cloudreve.redirect-uri=https://blog.example/api/admin/media/cloudreve/callback",
                        "blog.media.cloudreve.client-id=client-id",
                        "blog.media.cloudreve.client-secret=client-secret",
                        "blog.media.cloudreve.token-encryption-key=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CloudreveProperties.class);
                });
    }

    @Test
    void requiresCompleteCloudreveConfigurationWhenItIsTheDefaultUploadProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(CloudreveConfiguration.class)
                .withPropertyValues("blog.media.provider=cloudreve")
                .run(context -> assertThat(context).hasFailed());
    }

    private static CloudreveProperties configuredProperties() {
        CloudreveProperties properties = new CloudreveProperties();
        properties.setBaseUrl(URI.create("https://cloud.example"));
        properties.setRedirectUri(URI.create("https://blog.example/api/admin/media/cloudreve/callback"));
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setTokenEncryptionKey("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        return properties;
    }
}
