package com.blog.media;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.media.storage.ObjectLocation;
import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/** Small committed transactions around the remote-I/O portion of media deletion. */
@Service
public class MediaDeletionTransactionService {
    private final MediaAssetRepository mediaRepository;
    private final AdminAccountRepository adminRepository;
    private final MediaReferenceChecker referenceChecker;
    private final Clock clock;

    public MediaDeletionTransactionService(MediaAssetRepository mediaRepository, AdminAccountRepository adminRepository,
                                           MediaReferenceChecker referenceChecker) {
        this(mediaRepository, adminRepository, referenceChecker, Clock.systemUTC());
    }

    MediaDeletionTransactionService(MediaAssetRepository mediaRepository, AdminAccountRepository adminRepository,
                                    MediaReferenceChecker referenceChecker, Clock clock) {
        this.mediaRepository = mediaRepository;
        this.adminRepository = adminRepository;
        this.referenceChecker = referenceChecker;
        this.clock = clock;
    }

    /** Commits DELETING before the caller performs provider I/O. */
    @Transactional
    public DeletionTarget beginOwned(long mediaId, String username) {
        AdminAccount administrator = currentAdministrator(username);
        MediaAsset asset = mediaRepository.lockById(mediaId)
                .orElseThrow(() -> missing(mediaId));
        if (!ownsForDeletion(asset, administrator)) {
            throw missing(mediaId);
        }
        if (asset.getStatus() == MediaStatus.DELETED) {
            return target(asset, false);
        }
        if (asset.getStatus() == MediaStatus.DELETING) {
            return target(asset, true);
        }
        if (asset.getStatus() != MediaStatus.READY) {
            throw new ConflictException("Only ready media can be deleted");
        }
        if (referenceChecker.isReferenced(mediaId)) {
            throw new ConflictException("Media asset is referenced and cannot be deleted");
        }
        asset.setStatus(MediaStatus.DELETING);
        asset.setUpdatedAt(clock.instant());
        mediaRepository.saveAndFlush(asset);
        return target(asset, true);
    }

    /** Locks and claims a bounded cleanup candidate before provider I/O. */
    @Transactional
    public Optional<DeletionTarget> claimCleanup(long mediaId, Instant expiredBefore) {
        MediaAsset asset = mediaRepository.lockById(mediaId).orElse(null);
        if (asset == null || asset.getStatus() == MediaStatus.DELETED) return Optional.empty();
        if (asset.getStatus() == MediaStatus.PENDING_UPLOAD || asset.getStatus() == MediaStatus.UPLOADING
                || asset.getStatus() == MediaStatus.VERIFYING) {
            if (asset.getUpdatedAt() == null || !asset.getUpdatedAt().isBefore(expiredBefore)) return Optional.empty();
            asset.setStatus(MediaStatus.ABANDONED);
            asset.setOperationToken(null);
        }
        if (asset.getStatus() != MediaStatus.ABANDONED && asset.getStatus() != MediaStatus.FAILED
                && asset.getStatus() != MediaStatus.DELETING) {
            return Optional.empty();
        }
        asset.setUpdatedAt(clock.instant());
        mediaRepository.saveAndFlush(asset);
        return Optional.of(target(asset, true));
    }

    /** Idempotently records the terminal state after an authoritative provider delete success. */
    @Transactional
    public void finalizeDeleted(long mediaId) {
        MediaAsset asset = mediaRepository.lockById(mediaId)
                .orElseThrow(() -> missing(mediaId));
        if (asset.getStatus() == MediaStatus.DELETED) return;
        if (asset.getStatus() != MediaStatus.DELETING && asset.getStatus() != MediaStatus.ABANDONED
                && asset.getStatus() != MediaStatus.FAILED) {
            throw new ConflictException("Media asset is not pending deletion");
        }
        asset.setStatus(MediaStatus.DELETED);
        asset.setUpdatedAt(clock.instant());
        mediaRepository.saveAndFlush(asset);
    }

    public boolean ownsForDeletion(MediaAsset asset, AdminAccount administrator) {
        if (asset.getUploadedById() != null) return asset.getUploadedById().equals(administrator.getId());
        // V8 legacy rows predate upload ownership. Grandfather them only while this remains a single-admin blog.
        return adminRepository.countByEnabledTrue() == 1;
    }

    public AdminAccount currentAdministrator(String username) {
        if (username == null || username.isBlank()) throw missingAdministrator(username);
        return adminRepository.findByUsernameAndEnabledTrue(username.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> missingAdministrator(username));
    }

    private static DeletionTarget target(MediaAsset asset, boolean delete) {
        return new DeletionTarget(asset.getId(),
                new ObjectLocation(asset.getProvider(), asset.getBucket(), asset.getStorageKey()), delete);
    }

    private static ResourceNotFoundException missing(long mediaId) {
        return new ResourceNotFoundException("Media asset", Long.toString(mediaId));
    }

    private static ResourceNotFoundException missingAdministrator(String username) {
        return new ResourceNotFoundException("Administrator", username == null ? "current" : username);
    }

    public record DeletionTarget(long mediaId, ObjectLocation location, boolean requiresObjectDelete) {}
}
