package com.blog.media.storage.cloudreve;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Server-only Cloudreve OAuth and file-space settings. */
@ConfigurationProperties(prefix = "blog.media.cloudreve")
public class CloudreveProperties {
    private boolean enabled;
    private URI baseUrl;
    private URI authorizationUri;
    private URI tokenUri;
    private URI refreshUri;
    private URI userInfoUri;
    private URI redirectUri;
    private String clientId;
    private String clientSecret;
    private String policyId;
    private String rootPath = "/blog";
    private String tokenEncryptionKey;
    private boolean allowTrustedInternalHttp;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private List<URI> providerOrigins = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public URI getAuthorizationUri() { return authorizationUri; }
    public void setAuthorizationUri(URI authorizationUri) { this.authorizationUri = authorizationUri; }
    public URI getTokenUri() { return tokenUri; }
    public void setTokenUri(URI tokenUri) { this.tokenUri = tokenUri; }
    public URI getRefreshUri() { return refreshUri; }
    public void setRefreshUri(URI refreshUri) { this.refreshUri = refreshUri; }
    public URI getUserInfoUri() { return userInfoUri; }
    public void setUserInfoUri(URI userInfoUri) { this.userInfoUri = userInfoUri; }
    public URI getRedirectUri() { return redirectUri; }
    public void setRedirectUri(URI redirectUri) { this.redirectUri = redirectUri; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = trimmed(clientId); }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = trimmed(clientSecret); }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = trimmed(policyId); }
    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = normalizeRootPath(rootPath); }
    public String getTokenEncryptionKey() { return tokenEncryptionKey; }
    public void setTokenEncryptionKey(String tokenEncryptionKey) { this.tokenEncryptionKey = trimmed(tokenEncryptionKey); }
    public boolean isAllowTrustedInternalHttp() { return allowTrustedInternalHttp; }
    public void setAllowTrustedInternalHttp(boolean allowTrustedInternalHttp) {
        this.allowTrustedInternalHttp = allowTrustedInternalHttp;
    }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public List<URI> getProviderOrigins() { return List.copyOf(providerOrigins); }
    public void setProviderOrigins(List<URI> providerOrigins) {
        this.providerOrigins = providerOrigins == null ? new ArrayList<>() : new ArrayList<>(providerOrigins);
    }

    public URI authorizationUri() { return overrideOrResolve(authorizationUri, "/session/authorize"); }
    public URI tokenUri() { return overrideOrResolve(tokenUri, "/api/v4/session/oauth/token"); }
    public URI refreshUri() { return overrideOrResolve(refreshUri, "/api/v4/session/token/refresh"); }
    public URI userInfoUri() { return overrideOrResolve(userInfoUri, "/api/v4/session/oauth/userinfo"); }

    /** Validates settings only when Cloudreve is enabled for reads or selected for new uploads. */
    public void validate() {
        requireText(clientId, "Cloudreve client ID is required");
        requireText(clientSecret, "Cloudreve client secret is required");
        requireText(tokenEncryptionKey, "Cloudreve token encryption key is required");
        if (clientSecret.equals(tokenEncryptionKey)) {
            throw new IllegalArgumentException("Cloudreve token encryption key must differ from the client secret");
        }
        validateEncryptionKey(tokenEncryptionKey);
        validateBaseUrl(baseUrl);
        requireText(policyId, "Cloudreve storage policy ID is required");
        validateUri(authorizationUri(), "Cloudreve authorization URI");
        validateUri(tokenUri(), "Cloudreve token URI");
        validateUri(refreshUri(), "Cloudreve refresh URI");
        validateUri(userInfoUri(), "Cloudreve userinfo URI");
        validateUri(redirectUri, "Cloudreve redirect URI");
        validateTimeout(connectTimeout, "Cloudreve connect timeout");
        validateTimeout(requestTimeout, "Cloudreve request timeout");
        validateRootPath(rootPath);
        for (URI providerOrigin : providerOrigins) {
            validateProviderOrigin(providerOrigin);
        }
    }

    private URI overrideOrResolve(URI override, String defaultPath) {
        if (override != null) return override;
        if (baseUrl == null) {
            throw new IllegalArgumentException("Cloudreve base URL is required when OAuth endpoints are not configured");
        }
        return baseUrl.resolve(defaultPath);
    }

    private void validateUri(URI uri, String name) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(name + " must be an absolute HTTP(S) URL");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(name + " must not include credentials");
        }
        if (uri.getFragment() != null || uri.getHost().contains("*")) {
            throw new IllegalArgumentException(name + " must not include a fragment or wildcard host");
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) return;
        if (allowTrustedInternalHttp && "http".equalsIgnoreCase(uri.getScheme())) return;
        throw new IllegalArgumentException(name + " must use HTTPS unless trusted internal HTTP is enabled");
    }

    private void validateBaseUrl(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Cloudreve base URL is required");
        }
        validateUri(uri, "Cloudreve base URL");
        if (uri.getRawQuery() != null) {
            throw new IllegalArgumentException("Cloudreve base URL must not include a query");
        }
    }

    private static void validateEncryptionKey(String key) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cloudreve token encryption key must be base64-encoded AES-256 material", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Cloudreve token encryption key must contain exactly 32 bytes");
        }
    }

    private static void validateTimeout(Duration timeout, String name) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void validateRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank() || !rootPath.startsWith("/") || rootPath.contains("\\")
                || rootPath.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Cloudreve root path must be a normalized absolute logical path");
        }
        for (String segment : rootPath.split("/")) {
            if ("..".equals(segment) || ".".equals(segment)) {
                throw new IllegalArgumentException("Cloudreve root path must not contain traversal segments");
            }
        }
    }

    private void validateProviderOrigin(URI uri) {
        validateUri(uri, "Cloudreve provider origin");
        String path = uri.getRawPath();
        if ((path != null && !path.isEmpty() && !"/".equals(path)) || uri.getRawQuery() != null) {
            throw new IllegalArgumentException("Cloudreve provider origin must contain only scheme, host, and port");
        }
    }

    private static String normalizeRootPath(String value) {
        String trimmed = trimmed(value);
        if (trimmed == null || trimmed.isEmpty()) return trimmed;
        String normalized = trimmed.replaceAll("/+", "/");
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
