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
    private String bucket = "";
    private Path directory = Paths.get("./media");
    private long maxBytes = 5L * 1024 * 1024;
    private int maxDimension = 6000;
    private long maxAttachmentBytes = 20L * 1024 * 1024;
    private long maxZipAttachmentBytes = 50L * 1024 * 1024;
    private Duration uploadTtl = Duration.ofMinutes(10);

    public StorageProvider getProvider() {
        return provider;
    }

    public void setProvider(StorageProvider provider) {
        this.provider = provider;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket == null ? "" : bucket.trim();
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
}
