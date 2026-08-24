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
        assertThat(fixture.saved.getUploadedById()).isEqualTo(7L);
        assertThat(fixture.saved.getStorageKey()).matches("inline-images/[0-9a-f-]{36}\\.png");
    }

    @Test
    void returnsProviderDirectUploadTicketWithoutExposingObjectKey() {
        Fixture fixture = fixture(StorageProvider.R2, true);
        when(fixture.storage.createDirectUpload(any())).thenReturn(new UploadTicket(UploadMode.DIRECT, "PUT",
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

        verify(fixture.storage, never()).upload(any(), any());
    }

    @Test
    void completesProxyUploadOnlyAfterInspectingAndValidatingStoredContent() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        byte[] bytes = png();
        when(fixture.storage.inspect(asset.getStorageKey())).thenReturn(
                new StoredObject(asset.getStorageKey(), "image/png", bytes.length, "etag-1"));
        when(fixture.storage.openStream(asset.getStorageKey())).thenReturn(new ByteArrayInputStream(bytes));

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
        when(fixture.storage.inspect(asset.getStorageKey())).thenReturn(
                new StoredObject(asset.getStorageKey(), "image/png", 3, "etag-1"));
        when(fixture.storage.openStream(asset.getStorageKey())).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.complete(42L, "owner"));

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.FAILED);
        verify(fixture.storage).delete(asset.getStorageKey());
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
        when(fixture.storage.resolvePublicUrl(asset.getStorageKey())).thenReturn(URI.create("https://cdn.example/asset.png"));

        assertThat(fixture.service.resolvePublic(42L).location()).isEqualTo(URI.create("https://cdn.example/asset.png"));
    }

    @Test
    void abandonsExpiredPendingUploadAndRemovesItsObject() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setCreatedAt(NOW.minusSeconds(24 * 60 * 60 + 1));
        when(fixture.mediaRepository.findByStatusAndCreatedAtBefore(eq(MediaStatus.PENDING_UPLOAD), any()))
                .thenReturn(java.util.List.of(asset));

        assertThat(fixture.service.abandonExpiredUploads()).isEqualTo(1);

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.ABANDONED);
        verify(fixture.storage).delete(asset.getStorageKey());
    }

    @Test
    void refusesDeletionWhenAnyLegacyReferenceUsesTheMedia() throws Exception {
        Fixture fixture = fixture(StorageProvider.LOCAL, false);
        MediaAsset asset = pendingAsset(42L, 7L);
        asset.setStatus(MediaStatus.READY);
        when(fixture.mediaRepository.findByIdAndUploadedById(42L, 7L)).thenReturn(Optional.of(asset));
        when(fixture.referenceChecker.isReferenced(42L)).thenReturn(true);

        assertThatThrownBy(() -> fixture.service.delete(42L, "owner"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("referenced");

        verify(fixture.storage, never()).delete(any());
    }

    private Fixture fixture(StorageProvider provider, boolean directUpload) {
        MediaAssetRepository mediaRepository = mock(MediaAssetRepository.class);
        AdminAccountRepository adminRepository = mock(AdminAccountRepository.class);
        ObjectStorageRegistry registry = mock(ObjectStorageRegistry.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        MediaReferenceChecker referenceChecker = mock(MediaReferenceChecker.class);
        MediaProperties properties = new MediaProperties();
        properties.setProvider(provider);
        properties.setBucket(provider == StorageProvider.R2 ? "blog-media" : "");
        when(registry.get(provider)).thenReturn(storage);
        when(storage.capabilities()).thenReturn(new com.blog.media.storage.StorageCapabilities(directUpload, true));
        when(storage.provider()).thenReturn(provider);
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
                new MediaContentValidator(properties), referenceChecker, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, mediaRepository, storage, referenceChecker, saved);
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
                           MediaReferenceChecker referenceChecker, MediaAsset saved) {
    }
}
