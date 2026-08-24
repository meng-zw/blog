package com.blog.media;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.media.dto.MediaResponse;
import com.blog.media.dto.MediaUploadPlanResponse;
import com.blog.media.dto.MediaUploadRequest;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectStorageRegistry;
import com.blog.media.storage.StoredObject;
import com.blog.media.storage.UploadMode;
import com.blog.media.storage.UploadTicket;
import com.blog.media.storage.ObjectStorageException;
import com.blog.shared.error.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void createsPendingProxyUploadOwnedByCurrentAdministrator() {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);

        MediaUploadPlanResponse plan = fixture.service.requestUpload(
                new MediaUploadRequest("note.png", "image/png", 68, MediaPurpose.INLINE_IMAGE), "owner");

        assertThat(plan.uploadMode()).isEqualTo(UploadMode.PROXY);
        assertThat(plan.uploadUrl()).isEqualTo("/api/admin/media/uploads/42/content");
        assertThat(plan.headers()).containsEntry("Content-Type", "image/png");
        assertThat(fixture.saved.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        assertThat(fixture.saved.getBucket()).isEmpty();
        assertThat(fixture.saved.getUploadedById()).isEqualTo(7L);
        assertThat(fixture.saved.getStorageKey()).matches("inline-images/[0-9a-f-]{36}\\.png");
    }

    @Test
    void returnsProviderDirectUploadTicketWithoutExposingObjectKey() {
        Fixture fixture = fixture(StorageProvider.R2, true);
        when(fixture.storage.createDirectUpload(any(), any())).thenReturn(new UploadTicket(UploadMode.DIRECT, "PUT",
                URI.create("https://r2.example/upload"), Map.of("Content-Type", "image/png"), NOW.plusSeconds(600)));

        MediaUploadPlanResponse plan = fixture.service.requestUpload(
                new MediaUploadRequest("note.png", "image/png", 68, MediaPurpose.INLINE_IMAGE), "owner");

        assertThat(plan.uploadMode()).isEqualTo(UploadMode.DIRECT);
        assertThat(plan.uploadUrl()).isEqualTo("https://r2.example/upload");
        assertThat(plan.headers()).containsEntry("Content-Type", "image/png");
        assertThat(java.util.Arrays.stream(plan.getClass().getRecordComponents()).map(component -> component.getName()))
                .doesNotContain("storageKey");
    }

    @Test
    void rejectsProxyContentFromAnotherAdministrator() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.uploadProxyContent(42L, "other", new ByteArrayInputStream(png())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Media asset not found");

        verify(fixture.storage, never()).upload(any(), any(), any());
    }

    @Test
    void completesProxyUploadOnlyAfterInspectingAndValidatingStoredContent() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        byte[] bytes = png();
        when(fixture.storage.inspect(location(asset))).thenReturn(
                new StoredObject(asset.getStorageKey(), "image/png", bytes.length, "etag-1"));
        when(fixture.storage.openStream(location(asset))).thenReturn(new ByteArrayInputStream(bytes));

        MediaResponse response = fixture.service.complete(42L, "owner");

        assertThat(response.url()).isEqualTo("/api/media/assets/42");
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(asset.getEtag()).isEqualTo("etag-1");
        assertThat(asset.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void marksFailedAndDeletesObjectWhenCompleteValidationFails() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        when(fixture.storage.inspect(location(asset))).thenReturn(
                new StoredObject(asset.getStorageKey(), "image/png", 3, "etag-1"));
        when(fixture.storage.openStream(location(asset))).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.complete(42L, "owner"));

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETED);
        verify(fixture.storage).delete(location(asset));
    }

    @Test
    void keepsPendingUploadRetryableWhenStorageInspectionIsTransientThenCompletesLater() throws Exception {
        Fixture fixture = fixture(StorageProvider.R2, true);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        byte[] bytes = png();
        when(fixture.storage.inspect(location(asset)))
                .thenThrow(ObjectStorageException.transientFailure("R2 HEAD timed out", new IOException("timeout")))
                .thenReturn(new StoredObject(asset.getStorageKey(), "image/png", bytes.length, "etag-1"));
        when(fixture.storage.openStream(location(asset))).thenReturn(new ByteArrayInputStream(bytes));

        assertThatThrownBy(() -> fixture.service.complete(42L, "owner"))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        verify(fixture.storage, never()).delete(any());

        assertThat(fixture.service.complete(42L, "owner").status()).isEqualTo(MediaStatus.READY);
    }

    @Test
    void keepsPendingUploadRetryableWhenStoredContentStreamFails() throws Exception {
        Fixture fixture = fixture(StorageProvider.R2, true);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        when(fixture.storage.inspect(location(asset))).thenReturn(
                new StoredObject(asset.getStorageKey(), "image/png", asset.getByteSize(), "etag-1"));
        when(fixture.storage.openStream(location(asset))).thenThrow(new IOException("GET timed out"));

        assertThatThrownBy(() -> fixture.service.complete(42L, "owner"))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        verify(fixture.storage, never()).delete(any());
    }

    @Test
    void keepsPendingUploadRetryableWhenNetworkStreamFailsDuringValidationRead() throws Exception {
        Fixture fixture = fixture(StorageProvider.R2, true);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        when(fixture.storage.inspect(location(asset))).thenReturn(
                new StoredObject(asset.getStorageKey(), "image/png", asset.getByteSize(), "etag-1"));
        when(fixture.storage.openStream(location(asset))).thenReturn(new java.io.InputStream() {
            @Override public int read() throws IOException { throw new IOException("socket reset"); }
        });

        assertThatThrownBy(() -> fixture.service.complete(42L, "owner"))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        verify(fixture.storage, never()).delete(any());
    }

    @Test
    void keepsPendingStateWhenStoredObjectIsNotYetVisible() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        when(fixture.storage.inspect(location(asset)))
                .thenThrow(ObjectStorageException.notFound("Media object not found", null));

        assertThatThrownBy(() -> fixture.service.complete(42L, "owner"))
                .isInstanceOf(ServiceUnavailableException.class);

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        verify(fixture.storage, never()).delete(any());
    }

    @Test
    void retainsFailedStateWhenValidationCleanupCannotDeleteTheObject() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        when(fixture.storage.inspect(location(asset))).thenThrow(new IllegalArgumentException("invalid object"));
        org.mockito.Mockito.doThrow(new IOException("offline")).when(fixture.storage).delete(location(asset));

        assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.complete(42L, "owner"));

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.FAILED);
    }

    @Test
    void completesReadyMediaIdempotentlyWithoutReinspectingObject() {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setStatus(MediaStatus.READY);
        asset.setConfirmedAt(NOW);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));

        MediaResponse response = fixture.service.complete(42L, "owner");

        assertThat(response.mediaId()).isEqualTo(42L);
        verify(fixture.storage, never()).inspect(any());
    }

    @Test
    void redirectsOnlyReadyMediaToItsCurrentProviderUrl() {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setStatus(MediaStatus.READY);
        when(fixture.mediaRepository.findById(42L)).thenReturn(Optional.of(asset));
        when(fixture.storage.resolvePublicUrl(location(asset))).thenReturn(URI.create("https://cdn.example/asset.png"));

        assertThat(fixture.service.resolvePublic(42L).location()).isEqualTo(URI.create("https://cdn.example/asset.png"));
    }

    @Test
    void readsPersistedR2BucketWhenLocalIsTheDefaultForNewUploads() {
        MediaAssetRepository mediaRepository = mock(MediaAssetRepository.class);
        ObjectStorageRegistry registry = mock(ObjectStorageRegistry.class);
        ObjectStorage r2 = mock(ObjectStorage.class);
        MediaProperties properties = new MediaProperties();
        properties.setProvider(StorageProvider.LOCAL);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("archive-media");
        asset.setStatus(MediaStatus.READY);
        com.blog.media.storage.ObjectLocation persisted = location(asset);
        when(mediaRepository.findById(42L)).thenReturn(Optional.of(asset));
        when(registry.get(StorageProvider.R2)).thenReturn(r2);
        when(r2.resolvePublicUrl(persisted)).thenReturn(URI.create("https://archive.example/asset.png"));
        MediaApplicationService service = new MediaApplicationService(mediaRepository,
                mock(AdminAccountRepository.class), registry, new MediaContentValidator(properties),
                mock(MediaReferenceChecker.class), properties, mock(MediaDeletionService.class),
                mock(MediaDeletionTransactionService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.resolvePublic(42L).location()).hasToString("https://archive.example/asset.png");
        verify(r2).resolvePublicUrl(persisted);
    }

    @Test
    void opensReadyAttachmentContentFromTheCurrentStorageProviderWithoutUsingItsPublicUrl() throws Exception {
        Fixture fixture = fixture(StorageProvider.R2, true);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setProvider(StorageProvider.R2);
        asset.setBucket("blog-media");
        asset.setPurpose(MediaPurpose.ATTACHMENT);
        asset.setContentType("application/pdf");
        asset.setByteSize(9L);
        asset.setOriginalFilename("资料.pdf");
        asset.setStatus(MediaStatus.READY);
        when(fixture.mediaRepository.findById(42L)).thenReturn(Optional.of(asset));
        when(fixture.storage.openStream(location(asset))).thenReturn(new ByteArrayInputStream("pdf-bytes".getBytes()));

        MediaApplicationService.PublicMediaContent content = fixture.service.openPublicDownload(42L);

        assertThat(content.content().readAllBytes()).isEqualTo("pdf-bytes".getBytes());
        assertThat(content.filename()).isEqualTo("资料.pdf");
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.byteSize()).isEqualTo(9L);
        verify(fixture.storage, never()).resolvePublicUrl(location(asset));
    }

    @Test
    void marksExpiredPendingUploadTerminalAfterRemovingItsObject() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        when(fixture.deletionService.cleanupBatch()).thenReturn(1);

        assertThat(fixture.service.abandonExpiredUploads()).isEqualTo(1);
        verify(fixture.deletionService).cleanupBatch();
    }

    @Test
    void retriesFailedDeletionForAbandonedUploadWithoutTouchingReadyMedia() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        when(fixture.deletionService.cleanupBatch()).thenReturn(0, 1, 0);
        assertThat(fixture.service.abandonExpiredUploads()).isZero();
        assertThat(fixture.service.abandonExpiredUploads()).isEqualTo(1);
        assertThat(fixture.service.abandonExpiredUploads()).isZero();
        verify(fixture.deletionService, org.mockito.Mockito.times(3)).cleanupBatch();
    }

    @Test
    void marksFailedValidationCleanupTerminalAfterObjectDeletion() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        when(fixture.deletionService.cleanupBatch()).thenReturn(1);

        assertThat(fixture.service.abandonExpiredUploads()).isEqualTo(1);
    }

    @Test
    void refusesDeletionWhenAnyLegacyReferenceUsesTheMedia() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        org.mockito.Mockito.doThrow(new com.blog.shared.error.ConflictException("Media asset is referenced"))
                .when(fixture.deletionService).deleteOwned(42L, "owner");

        assertThatThrownBy(() -> fixture.service.delete(42L, "owner"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("referenced");

        verify(fixture.deletionService).deleteOwned(42L, "owner");
    }

    @Test
    void listsReferenceStateWithOneBulkLookupInsteadOfOneCheckPerAsset() {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset first = pendingAsset(1L, 7L); first.setStatus(MediaStatus.READY);
        MediaAsset second = pendingAsset(2L, 7L); second.setStatus(MediaStatus.READY);
        when(fixture.mediaRepository.findAdminPage(eq(null), eq(null), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(first, second)));
        when(fixture.referenceChecker.referencedIds(List.of(1L, 2L))).thenReturn(Set.of(2L));

        when(fixture.deletionTransactions.ownsForDeletion(eq(first), any())).thenReturn(true);
        when(fixture.deletionTransactions.ownsForDeletion(eq(second), any())).thenReturn(true);

        var page = fixture.service.list(0, 24, null, null, "owner");

        assertThat(page.items()).extracting(item -> item.referenced()).containsExactly(false, true);
        assertThat(page.items()).extracting(item -> item.canDelete()).containsExactly(true, false);
        verify(fixture.referenceChecker).referencedIds(List.of(1L, 2L));
        verify(fixture.referenceChecker, never()).isReferenced(any(Long.class));
    }

    private Fixture fixture(StorageProvider provider, boolean directUpload) {
        MediaAssetRepository mediaRepository = mock(MediaAssetRepository.class);
        AdminAccountRepository adminRepository = mock(AdminAccountRepository.class);
        ObjectStorageRegistry registry = mock(ObjectStorageRegistry.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        MediaReferenceChecker referenceChecker = mock(MediaReferenceChecker.class);
        MediaDeletionTransactionService deletionTransactions = mock(MediaDeletionTransactionService.class);
        MediaDeletionService deletionService = mock(MediaDeletionService.class);
        MediaProperties properties = new MediaProperties();
        properties.setProvider(provider);
        when(registry.get(provider)).thenReturn(storage);
        when(storage.capabilities()).thenReturn(new com.blog.media.storage.StorageCapabilities(directUpload, true));
        when(storage.provider()).thenReturn(provider);
        when(storage.locationForNewObject(any())).thenAnswer(invocation -> new com.blog.media.storage.ObjectLocation(
                provider, provider == StorageProvider.LOCAL ? "" : "blog-media", invocation.getArgument(0)));
        AdminAccount account = new AdminAccount();
        account.setId(7L);
        when(adminRepository.findByUsernameAndEnabledTrue("owner")).thenReturn(Optional.of(account));
        AdminAccount other = new AdminAccount();
        other.setId(8L);
        when(adminRepository.findByUsernameAndEnabledTrue("other")).thenReturn(Optional.of(other));
        MediaAsset saved = new MediaAsset();
        saved.setId(42L);
        when(mediaRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset candidate = invocation.getArgument(0);
            if (candidate.getId() == null) {
                candidate.setId(42L);
            }
            copy(candidate, saved);
            return candidate;
        });
        MediaApplicationService service = new MediaApplicationService(mediaRepository, adminRepository, registry,
                new MediaContentValidator(properties), referenceChecker, properties, deletionService,
                deletionTransactions, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, mediaRepository, storage, referenceChecker, saved, deletionTransactions, deletionService);
    }

    private static MediaAsset pendingAsset(long id, long ownerId) {
        MediaAsset asset = new MediaAsset();
        asset.setId(id);
        asset.setProvider(StorageProvider.LOCAL);
        asset.setBucket("");
        asset.setStorageKey("inline-images/123e4567-e89b-12d3-a456-426614174000.png");
        asset.setStatus(MediaStatus.PENDING_UPLOAD);
        asset.setPurpose(MediaPurpose.INLINE_IMAGE);
        asset.setOriginalFilename("note.png");
        asset.setContentType("image/png");
        asset.setByteSize(png().length);
        asset.setUploadedById(ownerId);
        asset.setCreatedAt(NOW);
        asset.setUpdatedAt(NOW);
        return asset;
    }

    private static com.blog.media.storage.ObjectLocation location(MediaAsset asset) {
        return new com.blog.media.storage.ObjectLocation(asset.getProvider(), asset.getBucket(), asset.getStorageKey());
    }

    private static void copy(MediaAsset source, MediaAsset target) {
        target.setId(source.getId());
        target.setProvider(source.getProvider());
        target.setBucket(source.getBucket());
        target.setStorageKey(source.getStorageKey());
        target.setStatus(source.getStatus());
        target.setPurpose(source.getPurpose());
        target.setOriginalFilename(source.getOriginalFilename());
        target.setContentType(source.getContentType());
        target.setByteSize(source.getByteSize());
        target.setUploadedById(source.getUploadedById());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private static byte[] png() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private record Fixture(MediaApplicationService service, MediaAssetRepository mediaRepository, ObjectStorage storage,
                           MediaReferenceChecker referenceChecker, MediaAsset saved,
                           MediaDeletionTransactionService deletionTransactions, MediaDeletionService deletionService) {
    }
}
