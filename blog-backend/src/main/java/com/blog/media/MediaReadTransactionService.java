package com.blog.media;

import com.blog.media.storage.ObjectLocation;
import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns detached immutable media metadata from short read transactions. */
@Service
public class MediaReadTransactionService {
    private final MediaAssetRepository repository;

    public MediaReadTransactionService(MediaAssetRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ReadyMediaSnapshot readySnapshot(long mediaId) {
        MediaAsset asset = repository.findById(mediaId)
                .orElseThrow(() -> missing(mediaId));
        if (asset.getStatus() != MediaStatus.READY) throw missing(mediaId);
        return new ReadyMediaSnapshot(asset.getId(),
                new ObjectLocation(asset.getProvider(), asset.getBucket(), asset.getStorageKey()),
                asset.getContentType(), asset.getOriginalFilename(), asset.getByteSize(), asset.getPurpose());
    }

    private static ResourceNotFoundException missing(long mediaId) {
        return new ResourceNotFoundException("Media asset", Long.toString(mediaId));
    }

    public record ReadyMediaSnapshot(long mediaId, ObjectLocation location, String contentType,
                                     String filename, long byteSize, MediaPurpose purpose) { }
}
