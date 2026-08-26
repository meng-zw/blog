package com.blog.media.storage.cloudreve;

import com.blog.media.StorageProvider;
import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.ObjectStorageException;
import com.blog.media.storage.ObjectUploadRequest;
import com.blog.media.storage.StorageCapabilities;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudreveObjectStorageTest {
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");
    private static final String SOURCE_KEY = "inline-images/123e4567-e89b-12d3-a456-426614174000.png";
    private static final String STORED_KEY = "inline-images/2026/08/123e4567-e89b-12d3-a456-426614174000.png";
    private static final String ROOT_IDENTITY = "cloudreve://my/blog/media";

    @Test
    void createsProxyLocationsWithTheNormalizedPersistedRootIdentityAndImmutableDatedKey() {
        Fixture fixture = fixture("//blog//media//");

        ObjectLocation location = fixture.storage.locationForNewObject(SOURCE_KEY);

        assertThat(fixture.storage.provider()).isEqualTo(StorageProvider.CLOUDREVE);
        assertThat(fixture.storage.capabilities()).isEqualTo(new StorageCapabilities(false, true));
        assertThat(location).isEqualTo(new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY));
    }

    @Test
    void rejectsKeysAndPersistedRootsThatCouldEscapeOrRetargetTheConfiguredRoot() {
        Fixture fixture = fixture("/blog/media");

        assertThatThrownBy(() -> fixture.storage.locationForNewObject("../outside.png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixture.storage.inspect(new ObjectLocation(
                StorageProvider.CLOUDREVE, "cloudreve://my/other", STORED_KEY)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fixture.storage.openStream(new ObjectLocation(
                StorageProvider.CLOUDREVE, ROOT_IDENTITY, "inline-images/../outside.png")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(fixture.client, never()).inspect(org.mockito.ArgumentMatchers.anyString());
        verify(fixture.client, never()).open(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void delegatesUploadInspectionAndStreamingUsingOnlyThePersistedRelativeKey() throws Exception {
        Fixture fixture = fixture("/blog/media");
        ObjectLocation location = new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY);
        ObjectUploadRequest request = new ObjectUploadRequest(STORED_KEY, "image/png", 3, 10);
        ByteArrayInputStream upload = new ByteArrayInputStream(new byte[]{1, 2, 3});
        CloudreveFileMetadata metadata = metadata();
        when(fixture.client.upload(STORED_KEY, request, upload)).thenReturn(metadata);
        when(fixture.client.inspect(STORED_KEY)).thenReturn(metadata);
        InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(fixture.client.open(STORED_KEY)).thenReturn(content);

        assertThat(fixture.storage.upload(location, request, upload))
                .isEqualTo(new com.blog.media.storage.StoredObject(STORED_KEY, "image/png", 3, "entity-etag"));
        assertThat(fixture.storage.inspect(location))
                .isEqualTo(new com.blog.media.storage.StoredObject(STORED_KEY, "image/png", 3, "entity-etag"));
        assertThat(fixture.storage.openStream(location)).isSameAs(content);

        verify(fixture.client).upload(STORED_KEY, request, upload);
        verify(fixture.client).inspect(STORED_KEY);
        verify(fixture.client).open(STORED_KEY);
    }

    @Test
    void mapsMissingAndProviderFailuresWithoutExposingCloudreveDetails() {
        Fixture fixture = fixture("/blog/media");
        ObjectLocation location = new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY);
        when(fixture.client.inspect(STORED_KEY))
                .thenThrow(new CloudreveApiException(CloudreveApiException.Kind.NOT_FOUND, "private path"))
                .thenThrow(new CloudreveApiException(CloudreveApiException.Kind.TRANSIENT, "bearer token rejected"));

        assertThatThrownBy(() -> fixture.storage.inspect(location))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(ObjectStorageException.Kind.NOT_FOUND))
                .hasMessageNotContaining("private path");
        assertThatThrownBy(() -> fixture.storage.inspect(location))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(ObjectStorageException.Kind.TRANSIENT))
                .hasMessageNotContaining("bearer token");
    }

    @Test
    void recoversAnAlreadyStoredMatchingUploadAfterAnUncertainDatabaseCommit() {
        Fixture fixture = fixture("/blog/media");
        ObjectLocation location = new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY);
        ObjectUploadRequest request = new ObjectUploadRequest(STORED_KEY, "image/png", 3, 10);
        ByteArrayInputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(fixture.client.upload(STORED_KEY, request, content)).thenThrow(
                new CloudreveApiException(CloudreveApiException.Kind.CONFLICT, "already exists"));
        when(fixture.client.inspect(STORED_KEY)).thenReturn(metadata());

        assertThat(fixture.storage.upload(location, request, content))
                .isEqualTo(new com.blog.media.storage.StoredObject(STORED_KEY, "image/png", 3, "entity-etag"));
        verify(fixture.client).inspect(STORED_KEY);
    }

    @Test
    void refusesToRecoverAConflictingObjectWhoseMetadataDoesNotMatchTheUpload() {
        Fixture fixture = fixture("/blog/media");
        ObjectLocation location = new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY);
        ObjectUploadRequest request = new ObjectUploadRequest(STORED_KEY, "image/png", 3, 10);
        ByteArrayInputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(fixture.client.upload(STORED_KEY, request, content)).thenThrow(
                new CloudreveApiException(CloudreveApiException.Kind.CONFLICT, "already exists"));
        when(fixture.client.inspect(STORED_KEY)).thenReturn(new CloudreveFileMetadata(
                ROOT_IDENTITY + "/" + STORED_KEY, "other-file", "image/jpeg", 4, "other-entity"));

        assertThatThrownBy(() -> fixture.storage.upload(location, request, content))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(ObjectStorageException.Kind.TRANSIENT));
    }

    @Test
    void treatsMissingDeleteAsSuccessAndMapsOtherDeleteFailuresAsRetryable() throws Exception {
        Fixture fixture = fixture("/blog/media");
        ObjectLocation location = new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY);
        org.mockito.Mockito.doThrow(new CloudreveApiException(CloudreveApiException.Kind.NOT_FOUND, "gone"))
                .doThrow(new CloudreveApiException(CloudreveApiException.Kind.PROVIDER_FAILURE, "internal response"))
                .when(fixture.client).delete(STORED_KEY);

        fixture.storage.delete(location);

        assertThatThrownBy(() -> fixture.storage.delete(location))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(ObjectStorageException.Kind.TRANSIENT))
                .hasMessageNotContaining("internal response");
    }

    @Test
    void neverResolvesACloudreveProviderUrlForTheBrowser() {
        Fixture fixture = fixture("/blog/media");
        ObjectLocation location = new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY);

        assertThatThrownBy(() -> fixture.storage.resolvePublicUrl(location))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(fixture.client, never()).open(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void remainsRegisteredForHistoricalReadsWhenLocalIsTheDefaultProvider() {
        new ApplicationContextRunner()
                .withUserConfiguration(CloudreveConfiguration.class, CloudreveObjectStorage.class)
                .withBean(CloudreveFileClient.class, () -> mock(CloudreveFileClient.class))
                .withPropertyValues(
                        "blog.media.provider=local",
                        "blog.media.cloudreve.enabled=true",
                        "blog.media.cloudreve.base-url=https://cloud.example",
                        "blog.media.cloudreve.redirect-uri=https://blog.example/api/admin/media/cloudreve/callback",
                        "blog.media.cloudreve.client-id=client-id",
                        "blog.media.cloudreve.client-secret=client-secret",
                        "blog.media.cloudreve.policy-id=policy-example",
                        "blog.media.cloudreve.root-path=/blog/media",
                        "blog.media.cloudreve.token-encryption-key=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CloudreveObjectStorage.class);
                    assertThat(context.getBean(CloudreveObjectStorage.class).provider())
                            .isEqualTo(StorageProvider.CLOUDREVE);
                });
    }

    @Test
    void mapsReauthorizationDuringAReadToRetryableProviderFailure() {
        Fixture fixture = fixture("/blog/media");
        ObjectLocation location = new ObjectLocation(StorageProvider.CLOUDREVE, ROOT_IDENTITY, STORED_KEY);
        when(fixture.client.open(STORED_KEY)).thenThrow(
                new CloudreveApiException(CloudreveApiException.Kind.TRANSIENT, "reauthorization required"));

        assertThatThrownBy(() -> fixture.storage.openStream(location))
                .isInstanceOfSatisfying(ObjectStorageException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(ObjectStorageException.Kind.TRANSIENT))
                .hasMessageNotContaining("reauthorization");
    }

    private static Fixture fixture(String rootPath) {
        CloudreveProperties properties = new CloudreveProperties();
        properties.setRootPath(rootPath);
        CloudreveFileClient client = mock(CloudreveFileClient.class);
        CloudreveObjectStorage storage = new CloudreveObjectStorage(properties, client,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(storage, client);
    }

    private static CloudreveFileMetadata metadata() {
        return new CloudreveFileMetadata(ROOT_IDENTITY + "/" + STORED_KEY, "file-id", "image/png", 3,
                "entity-etag");
    }

    private record Fixture(CloudreveObjectStorage storage, CloudreveFileClient client) { }
}
