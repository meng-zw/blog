package com.blog.media;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.media.storage.StoredObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaOperationTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void claimsVerificationUnderRowLockAndCompletesOnlyWithMatchingToken() {
        Fixture fixture = fixture();
        MediaAsset asset = pending();
        when(fixture.repository.lockById(42L)).thenReturn(Optional.of(asset));

        var claim = fixture.service.claimVerification(42L, "owner");

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.VERIFYING);
        assertThat(asset.getOperationToken()).isEqualTo(claim.operationToken());
        verify(fixture.repository).saveAndFlush(asset);

        var ready = fixture.service.completeVerification(claim,
                new StoredObject(asset.getStorageKey(), "image/png", 68, "etag"),
                new MediaContentValidator.ValidatedContent(1, 1));
        assertThat(ready.status()).isEqualTo(MediaStatus.READY);
        assertThat(asset.getOperationToken()).isNull();
    }

    @Test
    void persistsAuthoritativeFailureBeforeItCanBeCleaned() {
        Fixture fixture = fixture();
        MediaAsset asset = pending();
        asset.setStatus(MediaStatus.VERIFYING);
        asset.setOperationToken("token-1");
        when(fixture.repository.lockById(42L)).thenReturn(Optional.of(asset));
        var claim = MediaOperationTransactionService.OperationClaim.from(asset, "token-1");

        fixture.service.failVerification(claim);

        assertThat(asset.getStatus()).isEqualTo(MediaStatus.FAILED);
        assertThat(asset.getOperationToken()).isNull();
        verify(fixture.repository).saveAndFlush(asset);
    }

    @Test
    void proxyUploadUsesASeparateDurableClaimAndReturnsToPendingAfterStorageIo() {
        Fixture fixture = fixture();
        MediaAsset asset = pending();
        when(fixture.repository.lockById(42L)).thenReturn(Optional.of(asset));

        var claim = fixture.service.claimProxyUpload(42L, "owner");
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.UPLOADING);

        fixture.service.finishProxyUpload(claim);
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        assertThat(asset.getOperationToken()).isNull();
    }

    @Test
    void refusesStaleClaimsAfterCleanupHasMovedTheRowToAbandoned() {
        Fixture fixture = fixture();
        MediaAsset asset = pending();
        asset.setStatus(MediaStatus.ABANDONED);
        asset.setOperationToken(null);
        when(fixture.repository.lockById(42L)).thenReturn(Optional.of(asset));
        var stale = MediaOperationTransactionService.OperationClaim.from(asset, "stale-token");

        assertThatThrownBy(() -> fixture.service.completeVerification(stale,
                new StoredObject(asset.getStorageKey(), "image/png", 68, "etag"),
                new MediaContentValidator.ValidatedContent(1, 1)))
                .hasMessageContaining("claim");
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.ABANDONED);
    }

    private Fixture fixture() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        AdminAccountRepository administrators = mock(AdminAccountRepository.class);
        AdminAccount owner = new AdminAccount(); owner.setId(7L);
        when(administrators.findByUsernameAndEnabledTrue("owner")).thenReturn(Optional.of(owner));
        return new Fixture(new MediaOperationTransactionService(repository, administrators,
                Clock.fixed(NOW, ZoneOffset.UTC)), repository);
    }

    private static MediaAsset pending() {
        MediaAsset asset = new MediaAsset(); asset.setId(42L); asset.setProvider(StorageProvider.LOCAL); asset.setBucket("");
        asset.setStorageKey("inline-images/123e4567-e89b-12d3-a456-426614174000.png");
        asset.setStatus(MediaStatus.PENDING_UPLOAD); asset.setPurpose(MediaPurpose.INLINE_IMAGE);
        asset.setOriginalFilename("note.png"); asset.setContentType("image/png"); asset.setByteSize(68);
        asset.setUploadedById(7L); asset.setCreatedAt(NOW); asset.setUpdatedAt(NOW);
        return asset;
    }

    private record Fixture(MediaOperationTransactionService service, MediaAssetRepository repository) {}
}
