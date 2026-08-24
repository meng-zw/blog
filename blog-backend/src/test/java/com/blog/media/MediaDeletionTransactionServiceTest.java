package com.blog.media;

import com.blog.identity.AdminAccount;
import com.blog.identity.AdminAccountRepository;
import com.blog.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaDeletionTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void grandfathersUnownedLegacyMediaOnlyForTheSoleEnabledAdministrator() {
        Fixture fixture = fixture();
        MediaAsset legacy = ready(42L, null);
        when(fixture.mediaRepository.lockById(42L)).thenReturn(Optional.of(legacy));
        when(fixture.adminRepository.countByEnabledTrue()).thenReturn(1L);

        var target = fixture.service.beginOwned(42L, "owner");

        assertThat(target.requiresObjectDelete()).isTrue();
        assertThat(legacy.getStatus()).isEqualTo(MediaStatus.DELETING);
        verify(fixture.mediaRepository).saveAndFlush(legacy);
    }

    @Test
    void refusesUnownedLegacyMediaWhenMoreThanOneAdministratorIsEnabled() {
        Fixture fixture = fixture();
        MediaAsset legacy = ready(42L, null);
        when(fixture.mediaRepository.lockById(42L)).thenReturn(Optional.of(legacy));
        when(fixture.adminRepository.countByEnabledTrue()).thenReturn(2L);

        assertThatThrownBy(() -> fixture.service.beginOwned(42L, "owner"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(fixture.mediaRepository, never()).saveAndFlush(legacy);
    }

    @Test
    void persistsDeletingBeforeReturningTargetAndFinalizesIdempotently() {
        Fixture fixture = fixture();
        MediaAsset asset = ready(42L, 7L);
        when(fixture.mediaRepository.lockById(42L)).thenReturn(Optional.of(asset));

        fixture.service.beginOwned(42L, "owner");
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETING);

        fixture.service.finalizeDeleted(42L);
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETED);
        verify(fixture.mediaRepository, org.mockito.Mockito.times(2)).saveAndFlush(asset);
    }

    @Test
    void rejectsReferencedReadyMediaBeforeEnteringDeleting() {
        Fixture fixture = fixture();
        MediaAsset asset = ready(42L, 7L);
        when(fixture.mediaRepository.lockById(42L)).thenReturn(Optional.of(asset));
        when(fixture.referenceChecker.isReferenced(42L)).thenReturn(true);

        assertThatThrownBy(() -> fixture.service.beginOwned(42L, "owner"))
                .hasMessageContaining("referenced");
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.READY);
    }

    @Test
    void rotatesRetryCandidatesByRecordingEachCleanupClaim() {
        Fixture fixture = fixture();
        MediaAsset deleting = ready(42L, 7L);
        deleting.setStatus(MediaStatus.DELETING);
        deleting.setUpdatedAt(NOW.minusSeconds(3600));
        when(fixture.mediaRepository.lockById(42L)).thenReturn(Optional.of(deleting));

        assertThat(fixture.service.claimCleanup(42L, NOW.minusSeconds(86400))).isPresent();

        assertThat(deleting.getUpdatedAt()).isEqualTo(NOW);
        verify(fixture.mediaRepository).saveAndFlush(deleting);
    }

    @Test
    void cleanupCannotClaimAnActiveVerificationLease() {
        Fixture fixture = fixture();
        MediaAsset verifying = ready(42L, 7L);
        verifying.setStatus(MediaStatus.VERIFYING);
        verifying.setOperationToken("token");
        verifying.setUpdatedAt(NOW);
        when(fixture.mediaRepository.lockById(42L)).thenReturn(Optional.of(verifying));

        assertThat(fixture.service.claimCleanup(42L, NOW.minusSeconds(86400))).isEmpty();
        assertThat(verifying.getStatus()).isEqualTo(MediaStatus.VERIFYING);
    }

    @Test
    void cleanupRecoversAnExpiredVerificationClaimWithoutLeavingItsToken() {
        Fixture fixture = fixture();
        MediaAsset verifying = ready(42L, 7L);
        verifying.setStatus(MediaStatus.VERIFYING);
        verifying.setOperationToken("crashed-worker");
        verifying.setUpdatedAt(NOW.minusSeconds(86401));
        when(fixture.mediaRepository.lockById(42L)).thenReturn(Optional.of(verifying));

        assertThat(fixture.service.claimCleanup(42L, NOW.minusSeconds(86400))).isPresent();

        assertThat(verifying.getStatus()).isEqualTo(MediaStatus.ABANDONED);
        assertThat(verifying.getOperationToken()).isNull();
    }

    private Fixture fixture() {
        MediaAssetRepository mediaRepository = mock(MediaAssetRepository.class);
        AdminAccountRepository adminRepository = mock(AdminAccountRepository.class);
        MediaReferenceChecker referenceChecker = mock(MediaReferenceChecker.class);
        AdminAccount owner = new AdminAccount(); owner.setId(7L);
        when(adminRepository.findByUsernameAndEnabledTrue("owner")).thenReturn(Optional.of(owner));
        return new Fixture(new MediaDeletionTransactionService(mediaRepository, adminRepository, referenceChecker,
                Clock.fixed(NOW, ZoneOffset.UTC)), mediaRepository, adminRepository, referenceChecker);
    }

    private static MediaAsset ready(long id, Long ownerId) {
        MediaAsset asset = new MediaAsset(); asset.setId(id); asset.setProvider(StorageProvider.LOCAL); asset.setBucket("");
        asset.setStorageKey("inline-images/123e4567-e89b-12d3-a456-426614174000.png");
        asset.setStatus(MediaStatus.READY); asset.setUploadedById(ownerId); asset.setUpdatedAt(NOW);
        return asset;
    }

    private record Fixture(MediaDeletionTransactionService service, MediaAssetRepository mediaRepository,
                           AdminAccountRepository adminRepository, MediaReferenceChecker referenceChecker) {}
}
