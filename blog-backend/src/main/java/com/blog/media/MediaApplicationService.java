package com.blog.media;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.media.dto.MediaResponse;
import com.blog.media.dto.MediaUploadPlanResponse;
import com.blog.media.dto.MediaUploadRequest;
import com.blog.media.dto.AdminMediaAssetResponse;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectStorageRegistry;
import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.ObjectUploadRequest;
import com.blog.media.storage.StoredObject;
import com.blog.media.storage.UploadMode;
import com.blog.media.storage.UploadTicket;
import com.blog.shared.error.ConflictException;
import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.blog.shared.web.PageResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Owns media upload state transitions while storage adapters only manipulate objects. */
@Service
public class MediaApplicationService {
    private static final Duration PENDING_EXPIRY = Duration.ofHours(24);

    private final MediaAssetRepository mediaRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final ObjectStorageRegistry storageRegistry;
    private final MediaContentValidator contentValidator;
    private final MediaReferenceChecker referenceChecker;
    private final MediaProperties properties;
    private final Clock clock;

    public MediaApplicationService(MediaAssetRepository mediaRepository, AdminAccountRepository adminAccountRepository,
                                   ObjectStorageRegistry storageRegistry, MediaContentValidator contentValidator,
                                   MediaReferenceChecker referenceChecker, MediaProperties properties) {
        this(mediaRepository, adminAccountRepository, storageRegistry, contentValidator, referenceChecker, properties,
                Clock.systemUTC());
    }

    MediaApplicationService(MediaAssetRepository mediaRepository, AdminAccountRepository adminAccountRepository,
                            ObjectStorageRegistry storageRegistry, MediaContentValidator contentValidator,
                            MediaReferenceChecker referenceChecker, MediaProperties properties, Clock clock) {
        this.mediaRepository = mediaRepository;
        this.adminAccountRepository = adminAccountRepository;
        this.storageRegistry = storageRegistry;
        this.contentValidator = contentValidator;
        this.referenceChecker = referenceChecker;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public MediaUploadPlanResponse requestUpload(MediaUploadRequest request, String username) {
        contentValidator.validateDeclaration(request.purpose(), request.filename(), request.contentType(), request.byteSize());
        AdminAccount owner = currentAdministrator(username);
        ObjectStorage storage = storageRegistry.get(properties.getProvider());
        ObjectLocation newLocation = storage.locationForNewObject(objectKey(request.purpose(), request.contentType()));
        Instant now = clock.instant();
        MediaAsset asset = new MediaAsset();
        asset.setProvider(newLocation.provider());
        asset.setBucket(newLocation.bucket());
        asset.setStorageKey(newLocation.objectKey());
        asset.setStatus(MediaStatus.PENDING_UPLOAD);
        asset.setPurpose(request.purpose());
        asset.setOriginalFilename(request.filename().strip());
        asset.setContentType(normalizeContentType(request.contentType()));
        asset.setByteSize(request.byteSize());
        asset.setUploadedById(owner.getId());
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        asset = mediaRepository.save(asset);

        ObjectUploadRequest objectRequest = new ObjectUploadRequest(asset.getStorageKey(), asset.getContentType(), asset.getByteSize());
        UploadTicket ticket = storage.capabilities().directUpload()
                ? directTicket(storage, location(asset), objectRequest)
                : new UploadTicket(UploadMode.PROXY, "PUT", URI.create("/api/admin/media/uploads/" + asset.getId() + "/content"),
                Map.of("Content-Type", asset.getContentType()), now.plus(properties.getUploadTtl()));
        return new MediaUploadPlanResponse(asset.getId(), ticket.mode(), ticket.method(), ticket.uri().toString(),
                ticket.requiredHeaders(), ticket.expiresAt());
    }

    @Transactional
    public void uploadProxyContent(long mediaId, String username, InputStream content) {
        MediaAsset asset = ownedAsset(mediaId, username);
        if (asset.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new ConflictException("Media asset is not waiting for upload");
        }
        ObjectStorage storage = storage(asset);
        if (storage.capabilities().directUpload()) {
            throw new ConflictException("This media asset requires a direct upload");
        }
        try {
            InputStream limitedContent = contentValidator.limitProxyUpload(asset.getPurpose(), asset.getContentType(), content);
            storage.upload(location(asset), new ObjectUploadRequest(asset.getStorageKey(), asset.getContentType(), asset.getByteSize()), limitedContent);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to store uploaded media", exception);
        }
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public MediaResponse complete(long mediaId, String username) {
        MediaAsset asset = ownedAsset(mediaId, username);
        if (asset.getStatus() == MediaStatus.READY) {
            return response(asset);
        }
        if (asset.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new ConflictException("Media asset cannot be completed from status " + asset.getStatus());
        }
        ObjectStorage storage = storage(asset);
        try {
            StoredObject stored = storage.inspect(location(asset));
            verifyStoredObject(asset, stored);
            MediaContentValidator.ValidatedContent content;
            try (InputStream stream = storage.openStream(location(asset))) {
                content = contentValidator.validateStoredContent(asset.getPurpose(), asset.getContentType(), stream);
            }
            Instant now = clock.instant();
            asset.setContentType(normalizeContentType(stored.contentType()));
            asset.setByteSize(stored.byteSize());
            asset.setEtag(stored.etag());
            asset.setWidth(content.width());
            asset.setHeight(content.height());
            asset.setStatus(MediaStatus.READY);
            asset.setConfirmedAt(now);
            asset.setUpdatedAt(now);
            mediaRepository.save(asset);
            return response(asset);
        } catch (IOException exception) {
            failUpload(asset, storage);
            throw new IllegalArgumentException("Unable to read stored media", exception);
        } catch (RuntimeException exception) {
            failUpload(asset, storage);
            throw new IllegalArgumentException("Unable to verify stored media", exception);
        }
    }

    @Transactional
    public void delete(long mediaId, String username) {
        MediaAsset asset = ownedAssetForDeletion(mediaId, username);
        if (asset.getStatus() == MediaStatus.DELETED) {
            return;
        }
        if (asset.getStatus() != MediaStatus.READY) {
            throw new ConflictException("Only ready media can be deleted");
        }
        if (referenceChecker.isReferenced(mediaId)) {
            throw new ConflictException("Media asset is referenced and cannot be deleted");
        }
        try {
            storage(asset).delete(location(asset));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to delete media object", exception);
        }
        asset.setStatus(MediaStatus.DELETED);
        asset.setUpdatedAt(clock.instant());
        mediaRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminMediaAssetResponse> list(int page, int size, MediaStatus status, MediaPurpose purpose) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Page size must be between 1 and 100");
        var assets = mediaRepository.findAdminPage(status, purpose,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        var referencedIds = referenceChecker.referencedIds(assets.getContent().stream().map(MediaAsset::getId).toList());
        return PageResponse.from(assets.map(asset -> new AdminMediaAssetResponse(asset.getId(), asset.getOriginalFilename(), asset.getContentType(),
                        asset.getByteSize(), asset.getWidth(), asset.getHeight(), asset.getProvider(), asset.getStatus(),
                        asset.getPurpose(), referencedIds.contains(asset.getId()), stableUrl(asset), asset.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public PublicMediaAsset resolvePublic(long mediaId) {
        MediaAsset asset = readyPublicAsset(mediaId);
        return new PublicMediaAsset(storage(asset).resolvePublicUrl(location(asset)), asset.getContentType(),
                asset.getOriginalFilename(), asset.getPurpose());
    }

    /**
     * Opens a public attachment through the active provider without exposing a provider URL to the browser.
     * The controller owns closing this stream after it has copied it to the HTTP response.
     */
    @Transactional(readOnly = true)
    public PublicMediaContent openPublicDownload(long mediaId) {
        MediaAsset asset = readyPublicAsset(mediaId);
        try {
            return new PublicMediaContent(storage(asset).openStream(location(asset)), asset.getContentType(),
                    asset.getOriginalFilename(), asset.getByteSize());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to open media object", exception);
        }
    }

    @Transactional
    public int abandonExpiredUploads() {
        Instant expiredBefore = clock.instant().minus(PENDING_EXPIRY);
        int cleaned = 0;
        List<MediaAsset> retryableAssets = mediaRepository.findByStatusIn(List.of(MediaStatus.ABANDONED, MediaStatus.FAILED));
        for (MediaAsset asset : mediaRepository.findByStatusAndCreatedAtBefore(MediaStatus.PENDING_UPLOAD, expiredBefore)) {
            asset.setStatus(deleteObjectBestEffort(asset) ? MediaStatus.DELETED : MediaStatus.ABANDONED);
            asset.setUpdatedAt(clock.instant());
            mediaRepository.save(asset);
            cleaned++;
        }
        for (MediaAsset asset : retryableAssets) {
            if ((asset.getStatus() == MediaStatus.ABANDONED || asset.getStatus() == MediaStatus.FAILED)
                    && deleteObjectBestEffort(asset)) {
                asset.setStatus(MediaStatus.DELETED);
                asset.setUpdatedAt(clock.instant());
                mediaRepository.save(asset);
            }
            cleaned++;
        }
        return cleaned;
    }

    private UploadTicket directTicket(ObjectStorage storage, ObjectLocation location, ObjectUploadRequest request) {
        UploadTicket ticket = storage.createDirectUpload(location, request);
        if (ticket.mode() != UploadMode.DIRECT) {
            throw new IllegalStateException("Direct upload storage returned a non-direct upload ticket");
        }
        return ticket;
    }

    private void verifyStoredObject(MediaAsset asset, StoredObject object) {
        if (!asset.getStorageKey().equals(object.key())) {
            throw new IllegalArgumentException("Stored object key does not match media asset");
        }
        if (object.byteSize() != asset.getByteSize()) {
            throw new IllegalArgumentException("Uploaded object size does not match declared size");
        }
        if (!normalizeContentType(object.contentType()).equals(asset.getContentType())) {
            throw new IllegalArgumentException("Uploaded object content type does not match declared type");
        }
    }

    private void failUpload(MediaAsset asset, ObjectStorage storage) {
        try {
            storage.delete(location(asset));
        } catch (IOException | RuntimeException ignored) {
            // A scheduled cleanup can retry storage cleanup, but clients must never observe a false READY state.
        }
        asset.setStatus(MediaStatus.FAILED);
        asset.setUpdatedAt(clock.instant());
        mediaRepository.save(asset);
    }

    private boolean deleteObjectBestEffort(MediaAsset asset) {
        try {
            storage(asset).delete(location(asset));
            return true;
        } catch (IOException | RuntimeException ignored) {
            // ABANDONED and FAILED remain eligible for the next cleanup run until their provider delete succeeds.
            return false;
        }
    }

    private MediaAsset ownedAsset(long mediaId, String username) {
        AdminAccount administrator = currentAdministrator(username);
        return mediaRepository.findByIdAndUploadedById(mediaId, administrator.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", Long.toString(mediaId)));
    }

    private MediaAsset ownedAssetForDeletion(long mediaId, String username) {
        AdminAccount administrator = currentAdministrator(username);
        return mediaRepository.lockById(mediaId).filter(asset -> administrator.getId().equals(asset.getUploadedById()))
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", Long.toString(mediaId)));
    }

    private MediaAsset readyPublicAsset(long mediaId) {
        MediaAsset asset = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", Long.toString(mediaId)));
        if (asset.getStatus() != MediaStatus.READY) {
            throw new ResourceNotFoundException("Media asset", Long.toString(mediaId));
        }
        return asset;
    }

    private AdminAccount currentAdministrator(String username) {
        if (username == null || username.isBlank()) {
            throw new ResourceNotFoundException("Administrator", "current");
        }
        return adminAccountRepository.findByUsernameAndEnabledTrue(username.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Administrator", username));
    }

    private ObjectStorage storage(MediaAsset asset) {
        return storageRegistry.get(asset.getProvider());
    }

    private static ObjectLocation location(MediaAsset asset) {
        return new ObjectLocation(asset.getProvider(), asset.getBucket(), asset.getStorageKey());
    }

    private static MediaResponse response(MediaAsset asset) {
        return new MediaResponse(asset.getId(), asset.getOriginalFilename(), asset.getContentType(), asset.getByteSize(),
                asset.getWidth(), asset.getHeight(), asset.getStatus(), asset.getPurpose(), stableUrl(asset));
    }

    /** Provider-neutral URL used by every business response and persisted Markdown reference. */
    public static String stableUrl(MediaAsset asset) {
        return asset == null || asset.getId() == null ? null : "/api/media/assets/" + asset.getId();
    }

    private static String objectKey(MediaPurpose purpose, String contentType) {
        return keyPrefix(purpose) + "/" + UUID.randomUUID() + "." + extension(contentType);
    }

    private static String keyPrefix(MediaPurpose purpose) {
        return switch (purpose) {
            case AVATAR -> "avatars";
            case ARTICLE_COVER -> "article-covers";
            case TOPIC_COVER -> "topic-covers";
            case TOOL_COVER -> "tool-covers";
            case INLINE_IMAGE -> "inline-images";
            case ATTACHMENT -> "attachments";
        };
    }

    private static String extension(String contentType) {
        return switch (normalizeContentType(contentType)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "application/pdf" -> "pdf";
            case "application/zip" -> "zip";
            case "text/plain" -> "txt";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            default -> throw new IllegalArgumentException("Unsupported content type");
        };
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }
        int parameters = contentType.indexOf(';');
        return contentType.substring(0, parameters < 0 ? contentType.length() : parameters).trim().toLowerCase(Locale.ROOT);
    }

    public record PublicMediaAsset(URI location, String contentType, String filename, MediaPurpose purpose) {
    }

    public record PublicMediaContent(InputStream content, String contentType, String filename, long byteSize) {
    }
}
