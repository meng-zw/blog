package com.blog.media;

import com.blog.media.storage.LocalObjectStorage;
import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.ObjectUploadRequest;
import com.blog.media.storage.StoredObject;
import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Compatibility facade for legacy cover uploads and {@code /media/{storageKey}} reads.
 * New media workflows will use the media application service rather than this immediate-upload facade.
 */
@Service
public class MediaStorageService {
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/gif", "gif");

    private final MediaAssetRepository repository;
    private final MediaContentValidator contentValidator;
    private final LocalObjectStorage localObjectStorage;

    @Autowired
    public MediaStorageService(MediaAssetRepository repository, MediaContentValidator contentValidator,
                               LocalObjectStorage localObjectStorage) {
        this.repository = repository;
        this.contentValidator = contentValidator;
        this.localObjectStorage = localObjectStorage;
    }

    /** Kept for source compatibility with existing direct constructions. */
    @Deprecated(since = "media-v2")
    public MediaStorageService(MediaAssetRepository repository, MediaProperties properties) {
        this(repository, new MediaContentValidator(properties), new LocalObjectStorage(properties));
    }

    /** Legacy immediate image upload; all local I/O delegates to {@link LocalObjectStorage}. */
    @Deprecated(since = "media-v2")
    public MediaAsset store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("An image file is required");
        }
        String filename = file.getOriginalFilename();
        String contentType = normalizeContentType(file.getContentType());
        contentValidator.validateDeclaration(MediaPurpose.INLINE_IMAGE, filename, contentType, file.getSize());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded image", exception);
        }
        MediaContentValidator.ValidatedContent validated = contentValidator.validateStoredContent(
                MediaPurpose.INLINE_IMAGE, contentType, new ByteArrayInputStream(bytes));
        String storageKey = UUID.randomUUID() + "." + imageExtension(contentType);
        StoredObject stored;
        try {
            stored = localObjectStorage.upload(new ObjectLocation(StorageProvider.LOCAL, "", storageKey),
                    new ObjectUploadRequest(storageKey, contentType, bytes.length),
                    new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to store uploaded image", exception);
        }

        try {
            Instant now = Instant.now();
            MediaAsset asset = new MediaAsset();
            asset.setProvider(StorageProvider.LOCAL);
            asset.setBucket("");
            asset.setStorageKey(stored.key());
            asset.setStatus(MediaStatus.READY);
            asset.setPurpose(MediaPurpose.INLINE_IMAGE);
            asset.setOriginalFilename(filename);
            asset.setContentType(stored.contentType());
            asset.setByteSize(stored.byteSize());
            asset.setWidth(validated.width());
            asset.setHeight(validated.height());
            asset.setEtag(stored.etag());
            asset.setCreatedAt(now);
            asset.setConfirmedAt(now);
            asset.setUpdatedAt(now);
            return repository.save(asset);
        } catch (RuntimeException | Error exception) {
            try {
                localObjectStorage.delete(new ObjectLocation(StorageProvider.LOCAL, "", storageKey));
            } catch (IOException ignored) {
                // Preserve the database failure while making a best-effort object cleanup.
            }
            throw exception;
        }
    }

    @Deprecated(since = "media-v2")
    public Path load(String storageKey) {
        return localObjectStorage.loadPath(storageKey);
    }

    @Deprecated(since = "media-v2")
    public MediaAsset findByStorageKey(String storageKey) {
        return repository.findByProviderAndBucketAndStorageKey(StorageProvider.LOCAL, "", storageKey)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", storageKey));
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }
        int parameters = contentType.indexOf(';');
        return contentType.substring(0, parameters < 0 ? contentType.length() : parameters)
                .trim().toLowerCase(Locale.ROOT);
    }

    private static String imageExtension(String contentType) {
        String extension = IMAGE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("Only PNG, JPEG, or GIF uploads are allowed");
        }
        return extension;
    }
}
