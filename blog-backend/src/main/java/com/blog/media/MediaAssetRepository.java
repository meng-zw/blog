package com.blog.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    Optional<MediaAsset> findByStorageKey(String storageKey);
    Optional<MediaAsset> findByProviderAndBucketAndStorageKey(StorageProvider provider, String bucket, String storageKey);
    Optional<MediaAsset> findByIdAndUploadedById(Long id, Long uploadedById);
    List<MediaAsset> findByStatusAndCreatedAtBefore(MediaStatus status, Instant createdAt);
    List<MediaAsset> findByStatusIn(List<MediaStatus> statuses);
}
