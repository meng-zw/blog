package com.blog.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    Optional<MediaAsset> findByStorageKey(String storageKey);
    Optional<MediaAsset> findByProviderAndBucketAndStorageKey(StorageProvider provider, String bucket, String storageKey);
    Optional<MediaAsset> findByIdAndUploadedById(Long id, Long uploadedById);
    List<MediaAsset> findByStatusAndCreatedAtBefore(MediaStatus status, Instant createdAt);
    List<MediaAsset> findByStatusIn(List<MediaStatus> statuses);

    @Query("select media from MediaAsset media where (:status is null or media.status = :status) " +
            "and (:purpose is null or media.purpose = :purpose)")
    Page<MediaAsset> findAdminPage(MediaStatus status, MediaPurpose purpose, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from MediaAsset media where media.id = :id")
    Optional<MediaAsset> lockById(Long id);
}
