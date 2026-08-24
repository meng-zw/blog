package com.blog.media;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically closes abandoned upload sessions without deleting ready but unused media. */
@Component
public class MediaUploadCleanupJob {
    private final MediaDeletionService mediaDeletionService;

    public MediaUploadCleanupJob(MediaDeletionService mediaDeletionService) {
        this.mediaDeletionService = mediaDeletionService;
    }

    @Scheduled(cron = "0 17 * * * *", zone = "UTC")
    public void abandonExpiredUploads() {
        mediaDeletionService.cleanupBatch();
    }
}
