package com.blog.media.storage.r2;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** Server-only connection settings for Cloudflare R2's S3-compatible API. */
@ConfigurationProperties(prefix = "blog.media.r2")
public class R2Properties {
    private String accountId;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucket;
    private String endpoint;
    private String publicBaseUrl;
    private String region = "auto";
    private Duration uploadUrlTtl = Duration.ofMinutes(10);

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = trimmed(accountId);
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = trimmed(accessKeyId);
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = trimmed(secretAccessKey);
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = trimmed(bucket);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = trimmed(endpoint);
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = trimmed(publicBaseUrl);
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = trimmed(region);
    }

    public Duration getUploadUrlTtl() {
        return uploadUrlTtl;
    }

    public void setUploadUrlTtl(Duration uploadUrlTtl) {
        this.uploadUrlTtl = uploadUrlTtl;
    }

    public URI endpointUri() {
        String resolved = endpoint == null || endpoint.isBlank()
                ? "https://" + accountId + ".r2.cloudflarestorage.com" : endpoint;
        return URI.create(resolved);
    }

    public URI publicBaseUri() {
        return URI.create(publicBaseUrl);
    }

    /** Validated only when the R2 provider is selected by {@link R2Configuration}. */
    public void validate() {
        requireText(accountId, "R2 account ID is required");
        requireText(accessKeyId, "R2 access key ID is required");
        requireText(secretAccessKey, "R2 secret access key is required");
        requireText(bucket, "R2 bucket is required");
        requireText(publicBaseUrl, "R2 public base URL is required");
        if (!"auto".equals(region)) {
            throw new IllegalArgumentException("R2 region must be auto");
        }
        if (uploadUrlTtl == null || uploadUrlTtl.isZero() || uploadUrlTtl.isNegative()
                || uploadUrlTtl.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("R2 upload URL TTL must be between zero and seven days");
        }
        validateHttpsUri(endpointUri(), "R2 endpoint");
        validateHttpsUri(publicBaseUri(), "R2 public base URL");
    }

    private static void validateHttpsUri(URI uri, String name) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(name + " must be an absolute HTTPS URL without credentials, query, or fragment");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
