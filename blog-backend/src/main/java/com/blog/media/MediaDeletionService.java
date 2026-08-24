package com.blog.media;

import com.blog.media.storage.ObjectStorageRegistry;
import com.blog.media.storage.ObjectStorageException;
import com.blog.shared.error.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Coordinates provider deletion outside database transactions. */
@Service
public class MediaDeletionService {
    private static final Logger log = LoggerFactory.getLogger(MediaDeletionService.class);
    private static final Duration PENDING_EXPIRY = Duration.ofHours(24);
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final List<MediaStatus> EXPIRING = List.of(
            MediaStatus.PENDING_UPLOAD, MediaStatus.UPLOADING, MediaStatus.VERIFYING);
    private static final List<MediaStatus> RETRYABLE = List.of(MediaStatus.ABANDONED, MediaStatus.FAILED, MediaStatus.DELETING);

    private final MediaDeletionTransactionService transactions;
    private final MediaAssetRepository mediaRepository;
    private final ObjectStorageRegistry storageRegistry;
    private final Clock clock;

    public MediaDeletionService(MediaDeletionTransactionService transactions, MediaAssetRepository mediaRepository,
                                ObjectStorageRegistry storageRegistry) {
        this(transactions, mediaRepository, storageRegistry, Clock.systemUTC());
    }

    MediaDeletionService(MediaDeletionTransactionService transactions, MediaAssetRepository mediaRepository,
                         ObjectStorageRegistry storageRegistry, Clock clock) {
        this.transactions = transactions;
        this.mediaRepository = mediaRepository;
        this.storageRegistry = storageRegistry;
        this.clock = clock;
    }

    public void deleteOwned(long mediaId, String username) {
        var target = transactions.beginOwned(mediaId, username);
        if (!target.requiresObjectDelete()) return;
        try {
            storageRegistry.get(target.location().provider()).delete(target.location());
        } catch (Exception exception) {
            throw new ServiceUnavailableException("媒体存储暂时不可用，删除任务将自动重试", exception);
        }
        // If this commit fails, DELETING remains durable and the cleanup job idempotently finalizes it later.
        transactions.finalizeDeleted(mediaId);
    }

    public int cleanupBatch() {
        Instant expiredBefore = clock.instant().minus(PENDING_EXPIRY);
        List<Long> candidates = mediaRepository.findCleanupCandidateIds(EXPIRING, expiredBefore,
                RETRYABLE, PageRequest.of(0, CLEANUP_BATCH_SIZE));
        int completed = 0;
        for (Long mediaId : candidates) {
            if (cleanupOne(mediaId, expiredBefore)) completed++;
        }
        return completed;
    }

    /** One idempotent cleanup attempt, also used immediately after authoritative validation failure. */
    public boolean cleanupOne(long mediaId) {
        return cleanupOne(mediaId, clock.instant().minus(PENDING_EXPIRY));
    }

    private boolean cleanupOne(long mediaId, Instant expiredBefore) {
        MediaDeletionTransactionService.DeletionTarget target = null;
        try {
            var claimed = transactions.claimCleanup(mediaId, expiredBefore);
            if (claimed.isEmpty()) return false;
            target = claimed.get();
            storageRegistry.get(target.location().provider()).delete(target.location());
            transactions.finalizeDeleted(mediaId);
            return true;
        } catch (Exception exception) {
            String provider = target == null ? "UNKNOWN" : target.location().provider().name();
            log.warn("media cleanup failed mediaId={} provider={} category={}", mediaId, provider,
                    failureCategory(exception), exception);
            // A durable FAILED/ABANDONED/DELETING state remains eligible for the next bounded run.
            return false;
        }
    }

    private static String failureCategory(Exception exception) {
        if (exception instanceof ObjectStorageException storageException) return storageException.kind().name();
        if (exception instanceof java.io.IOException) return "IO";
        if (exception instanceof org.springframework.dao.DataAccessException) return "DATABASE";
        return exception.getClass().getSimpleName();
    }
}
