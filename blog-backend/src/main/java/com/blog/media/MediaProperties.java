package com.blog.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "blog.media")
public class MediaProperties {
    private StorageProvider provider = StorageProvider.LOCAL;
    private Path directory = Paths.get("./media");
    private long maxBytes = 5L * 1024 * 1024;
    private int maxDimension = 6000;
    private long maxAttachmentBytes = 20L * 1024 * 1024;
    private long maxZipAttachmentBytes = 50L * 1024 * 1024;
    private Duration uploadTtl = Duration.ofMinutes(10);
    private UploadPlanRateLimit uploadPlanRateLimit = new UploadPlanRateLimit();

    public StorageProvider getProvider() {
        return provider;
    }

    public void setProvider(StorageProvider provider) {
        this.provider = provider;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public void setMaxDimension(int maxDimension) {
        this.maxDimension = maxDimension;
    }

    public long getMaxAttachmentBytes() {
        return maxAttachmentBytes;
    }

    public void setMaxAttachmentBytes(long maxAttachmentBytes) {
        this.maxAttachmentBytes = maxAttachmentBytes;
    }

    public long getMaxZipAttachmentBytes() {
        return maxZipAttachmentBytes;
    }

    public void setMaxZipAttachmentBytes(long maxZipAttachmentBytes) {
        this.maxZipAttachmentBytes = maxZipAttachmentBytes;
    }

    public Duration getUploadTtl() {
        return uploadTtl;
    }

    public void setUploadTtl(Duration uploadTtl) {
        this.uploadTtl = uploadTtl;
    }

    public UploadPlanRateLimit getUploadPlanRateLimit() {
        return uploadPlanRateLimit;
    }

    public void setUploadPlanRateLimit(UploadPlanRateLimit uploadPlanRateLimit) {
        this.uploadPlanRateLimit = uploadPlanRateLimit == null ? new UploadPlanRateLimit() : uploadPlanRateLimit;
    }

    public static class UploadPlanRateLimit {
        private int maximumRequests = 30;
        private Duration window = Duration.ofMinutes(1);
        private int maximumEntries = 10_000;

        public int getMaximumRequests() { return maximumRequests; }
        public void setMaximumRequests(int maximumRequests) { this.maximumRequests = maximumRequests; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
        public int getMaximumEntries() { return maximumEntries; }
        public void setMaximumEntries(int maximumEntries) { this.maximumEntries = maximumEntries; }
    }
}
