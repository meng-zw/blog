package com.blog.media;

import com.blog.media.storage.ObjectStorageRegistry;
import com.blog.shared.error.ServiceUnavailableException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Coordinates provider deletion outside database transactions. */
@Service
public class MediaDeletionService {
    private static final Duration PENDING_EXPIRY = Duration.ofHours(24);
    private static final int CLEANUP_BATCH_SIZE = 100;
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
        List<Long> candidates = mediaRepository.findCleanupCandidateIds(MediaStatus.PENDING_UPLOAD, expiredBefore,
                RETRYABLE, PageRequest.of(0, CLEANUP_BATCH_SIZE));
        int completed = 0;
        for (Long mediaId : candidates) {
            var target = transactions.claimCleanup(mediaId, expiredBefore);
            if (target.isEmpty()) continue;
            try {
                storageRegistry.get(target.get().location().provider()).delete(target.get().location());
                transactions.finalizeDeleted(mediaId);
                completed++;
            } catch (Exception ignored) {
                // Durable ABANDONED/FAILED/DELETING state remains eligible for the next bounded run.
            }
        }
        return completed;
    }
}
