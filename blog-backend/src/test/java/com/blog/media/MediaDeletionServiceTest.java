package com.blog.media;

import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectStorageRegistry;
import com.blog.shared.error.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaDeletionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void deletesObjectOnlyAfterDeletingStateTransactionAndThenFinalizes() throws Exception {
        Fixture fixture = fixture();
        var target = target(42L);
        when(fixture.transactions.beginOwned(42L, "owner")).thenReturn(target);

        fixture.service.deleteOwned(42L, "owner");

        var order = inOrder(fixture.transactions, fixture.storage);
        order.verify(fixture.transactions).beginOwned(42L, "owner");
        order.verify(fixture.storage).delete(target.location());
        order.verify(fixture.transactions).finalizeDeleted(42L);
    }

    @Test
    void leavesDeletingRetryableWhenProviderDeleteFails() throws Exception {
        Fixture fixture = fixture();
        var target = target(42L);
        when(fixture.transactions.beginOwned(42L, "owner")).thenReturn(target);
        org.mockito.Mockito.doThrow(new IOException("offline")).when(fixture.storage).delete(target.location());

        assertThatThrownBy(() -> fixture.service.deleteOwned(42L, "owner"))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(fixture.transactions, never()).finalizeDeleted(42L);
    }

    @Test
    void retriesIdempotentlyWhenFinalDatabaseCommitFailsAfterObjectDeletion() throws Exception {
        Fixture fixture = fixture();
        var target = target(42L);
        when(fixture.transactions.beginOwned(42L, "owner")).thenReturn(target);
        org.mockito.Mockito.doThrow(new org.springframework.dao.TransientDataAccessResourceException("database offline"))
                .doNothing().when(fixture.transactions).finalizeDeleted(42L);

        assertThatThrownBy(() -> fixture.service.deleteOwned(42L, "owner"))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        fixture.service.deleteOwned(42L, "owner");

        verify(fixture.storage, org.mockito.Mockito.times(2)).delete(target.location());
        verify(fixture.transactions, org.mockito.Mockito.times(2)).finalizeDeleted(42L);
    }

    @Test
    void processesOnlyOneBoundedCleanupBatchAndRetriesDeletingAssets() throws Exception {
        Fixture fixture = fixture();
        when(fixture.mediaRepository.findCleanupCandidateIds(any(), any(), any(), any(Pageable.class))).thenReturn(List.of(42L, 43L));
        when(fixture.transactions.claimCleanup(42L, NOW.minusSeconds(86400))).thenReturn(java.util.Optional.of(target(42L)));
        when(fixture.transactions.claimCleanup(43L, NOW.minusSeconds(86400))).thenReturn(java.util.Optional.of(target(43L)));
        org.mockito.Mockito.doThrow(new IOException("offline")).when(fixture.storage).delete(target(42L).location());

        assertThat(fixture.service.cleanupBatch()).isEqualTo(1);

        verify(fixture.transactions, never()).finalizeDeleted(42L);
        verify(fixture.transactions).finalizeDeleted(43L);
        verify(fixture.mediaRepository).findCleanupCandidateIds(any(), any(), any(),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageSize() == 100));
    }

    private Fixture fixture() {
        MediaDeletionTransactionService transactions = mock(MediaDeletionTransactionService.class);
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        ObjectStorageRegistry registry = mock(ObjectStorageRegistry.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        when(registry.get(StorageProvider.LOCAL)).thenReturn(storage);
        return new Fixture(new MediaDeletionService(transactions, repository, registry,
                Clock.fixed(NOW, ZoneOffset.UTC)), transactions, repository, storage);
    }

    private static MediaDeletionTransactionService.DeletionTarget target(long id) {
        return new MediaDeletionTransactionService.DeletionTarget(id,
                new ObjectLocation(StorageProvider.LOCAL, "", String.format(
                        "inline-images/123e4567-e89b-12d3-a456-%012d.png", id)), true);
    }

    private record Fixture(MediaDeletionService service, MediaDeletionTransactionService transactions,
                           MediaAssetRepository mediaRepository, ObjectStorage storage) {}
}
