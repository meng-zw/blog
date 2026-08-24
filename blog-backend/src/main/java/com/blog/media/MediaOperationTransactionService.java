package com.blog.media;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.StoredObject;
import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/** Short, row-locked transactions that claim and finalize storage I/O without holding locks during I/O. */
@Service
public class MediaOperationTransactionService {
    private final MediaAssetRepository repository;
    private final AdminAccountRepository administrators;
    private final Clock clock;

    public MediaOperationTransactionService(MediaAssetRepository repository, AdminAccountRepository administrators) {
        this(repository, administrators, Clock.systemUTC());
    }

    MediaOperationTransactionService(MediaAssetRepository repository, AdminAccountRepository administrators, Clock clock) {
        this.repository = repository;
        this.administrators = administrators;
        this.clock = clock;
    }

    @Transactional
    public OperationClaim claimVerification(long mediaId, String username) {
        MediaAsset asset = ownedLocked(mediaId, username);
        if (asset.getStatus() == MediaStatus.READY) return OperationClaim.from(asset, null);
        if (asset.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new ConflictException("Media asset cannot be verified from status " + asset.getStatus());
        }
        return claim(asset, MediaStatus.VERIFYING);
    }

    @Transactional
    public OperationClaim claimProxyUpload(long mediaId, String username) {
        MediaAsset asset = ownedLocked(mediaId, username);
        if (asset.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new ConflictException("Media asset is not waiting for upload");
        }
        return claim(asset, MediaStatus.UPLOADING);
    }

    @Transactional
    public OperationClaim completeVerification(OperationClaim claim, StoredObject stored,
                                               MediaContentValidator.ValidatedContent content) {
        MediaAsset asset = requireClaim(claim, MediaStatus.VERIFYING);
        asset.setContentType(normalizeContentType(stored.contentType()));
        asset.setByteSize(stored.byteSize());
        asset.setEtag(stored.etag());
        asset.setWidth(content.width());
        asset.setHeight(content.height());
        asset.setStatus(MediaStatus.READY);
        asset.setOperationToken(null);
        asset.setConfirmedAt(clock.instant());
        asset.setUpdatedAt(clock.instant());
        repository.saveAndFlush(asset);
        return OperationClaim.from(asset, null);
    }

    @Transactional
    public void failVerification(OperationClaim claim) {
        MediaAsset asset = requireClaim(claim, MediaStatus.VERIFYING);
        asset.setStatus(MediaStatus.FAILED);
        asset.setOperationToken(null);
        asset.setUpdatedAt(clock.instant());
        repository.saveAndFlush(asset);
    }

    @Transactional
    public void releaseVerification(OperationClaim claim) {
        release(claim, MediaStatus.VERIFYING);
    }

    @Transactional
    public void finishProxyUpload(OperationClaim claim) {
        release(claim, MediaStatus.UPLOADING);
    }

    @Transactional
    public void releaseProxyUpload(OperationClaim claim) {
        release(claim, MediaStatus.UPLOADING);
    }

    private void release(OperationClaim claim, MediaStatus expected) {
        MediaAsset asset = requireClaim(claim, expected);
        asset.setStatus(MediaStatus.PENDING_UPLOAD);
        asset.setOperationToken(null);
        asset.setUpdatedAt(clock.instant());
        repository.saveAndFlush(asset);
    }

    private OperationClaim claim(MediaAsset asset, MediaStatus status) {
        String token = UUID.randomUUID().toString();
        asset.setStatus(status);
        asset.setOperationToken(token);
        asset.setUpdatedAt(clock.instant());
        repository.saveAndFlush(asset);
        return OperationClaim.from(asset, token);
    }

    private MediaAsset requireClaim(OperationClaim claim, MediaStatus expected) {
        MediaAsset asset = repository.lockById(claim.mediaId())
                .orElseThrow(() -> missing(claim.mediaId()));
        if (asset.getStatus() != expected || claim.operationToken() == null
                || !claim.operationToken().equals(asset.getOperationToken())) {
            throw new ConflictException("Media operation claim is stale");
        }
        return asset;
    }

    private MediaAsset ownedLocked(long mediaId, String username) {
        AdminAccount administrator = administrator(username);
        return repository.lockById(mediaId)
                .filter(asset -> administrator.getId().equals(asset.getUploadedById()))
                .orElseThrow(() -> missing(mediaId));
    }

    private AdminAccount administrator(String username) {
        if (username == null || username.isBlank()) throw new ResourceNotFoundException("Administrator", "current");
        return administrators.findByUsernameAndEnabledTrue(username.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Administrator", username));
    }

    private static ResourceNotFoundException missing(long mediaId) {
        return new ResourceNotFoundException("Media asset", Long.toString(mediaId));
    }

    private static String normalizeContentType(String contentType) {
        int parameters = contentType.indexOf(';');
        return contentType.substring(0, parameters < 0 ? contentType.length() : parameters).trim().toLowerCase(Locale.ROOT);
    }

    public record OperationClaim(long mediaId, ObjectLocation location, MediaPurpose purpose, String filename,
                                 String contentType, long byteSize, Integer width, Integer height,
                                 MediaStatus status, String operationToken) {
        public static OperationClaim from(MediaAsset asset, String token) {
            return new OperationClaim(asset.getId(), new ObjectLocation(asset.getProvider(), asset.getBucket(), asset.getStorageKey()),
                    asset.getPurpose(), asset.getOriginalFilename(), asset.getContentType(), asset.getByteSize(),
                    asset.getWidth(), asset.getHeight(), asset.getStatus(), token);
        }
    }
}
