package com.blog.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    Optional<MediaAsset> findByStorageKey(String storageKey);
    Optional<MediaAsset> findByProviderAndBucketAndStorageKey(StorageProvider provider, String bucket, String storageKey);
    Optional<MediaAsset> findByIdAndUploadedById(Long id, Long uploadedById);
}
