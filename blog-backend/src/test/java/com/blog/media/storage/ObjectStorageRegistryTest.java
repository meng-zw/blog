package com.blog.media.storage;

import com.blog.media.StorageProvider;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ObjectStorageRegistryTest {

    @Test
    void selectsStorageByProvider() {
        ObjectStorage local = new StubStorage(StorageProvider.LOCAL);
        ObjectStorage r2 = new StubStorage(StorageProvider.R2);

        ObjectStorageRegistry registry = new ObjectStorageRegistry(List.of(local, r2));

        assertThat(registry.get(StorageProvider.LOCAL)).isSameAs(local);
        assertThat(registry.get(StorageProvider.R2)).isSameAs(r2);
    }

    @Test
    void rejectsDuplicateProviders() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ObjectStorageRegistry(List.of(
                new StubStorage(StorageProvider.LOCAL), new StubStorage(StorageProvider.LOCAL))))
                .withMessage("Multiple object storage adapters are configured for provider LOCAL");
    }

    @Test
    void rejectsUnknownProvider() {
        ObjectStorageRegistry registry = new ObjectStorageRegistry(List.of(new StubStorage(StorageProvider.LOCAL)));

        assertThatIllegalArgumentException().isThrownBy(() -> registry.get(StorageProvider.R2))
                .withMessage("No object storage adapter is configured for provider R2");
    }

    private record StubStorage(StorageProvider provider) implements ObjectStorage {
        @Override
        public StorageCapabilities capabilities() {
            return new StorageCapabilities(false, true);
        }

        @Override
        public ObjectLocation locationForNewObject(String objectKey) {
            return new ObjectLocation(provider, provider == StorageProvider.LOCAL ? "" : "bucket", objectKey);
        }

        @Override
        public UploadTicket createDirectUpload(ObjectLocation location, ObjectUploadRequest request) {
            return new UploadTicket(UploadMode.DIRECT, "PUT", URI.create("https://storage.example/upload"),
                    Map.of("Content-Type", request.contentType()), Instant.parse("2026-08-24T00:10:00Z"));
        }

        @Override
        public StoredObject upload(ObjectLocation location, ObjectUploadRequest request, InputStream content) {
            return new StoredObject(request.objectKey(), request.contentType(), request.byteSize(), "etag");
        }

        @Override
        public StoredObject inspect(ObjectLocation location) {
            return new StoredObject(location.objectKey(), "image/png", 1, "etag");
        }

        @Override
        public InputStream openStream(ObjectLocation location) {
            return InputStream.nullInputStream();
        }

        @Override
        public URI resolvePublicUrl(ObjectLocation location) {
            return URI.create("https://storage.example/" + location.objectKey());
        }

        @Override
        public void delete(ObjectLocation location) {
        }
    }
}
