package com.blog.media.storage;

import com.blog.media.MediaProperties;
import com.blog.media.StorageProvider;
import com.blog.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalObjectStorageTest {

    @TempDir
    Path mediaDirectory;

    @Test
    void storesPurposePrefixedUuidKeyAtomicallyAndExposesProxyMetadata() throws Exception {
        LocalObjectStorage storage = storage();
        String key = "inline-images/" + UUID.randomUUID() + ".png";
        byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = storage.upload(new ObjectUploadRequest(key, "image/png", content.length),
                new ByteArrayInputStream(content));

        assertThat(storage.provider()).isEqualTo(StorageProvider.LOCAL);
        assertThat(storage.capabilities()).isEqualTo(new StorageCapabilities(false, true));
        assertThat(stored.key()).isEqualTo(key);
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.byteSize()).isEqualTo(content.length);
        assertThat(stored.etag()).isNotBlank();
        assertThat(Files.readAllBytes(mediaDirectory.resolve(key))).isEqualTo(content);
        try (var files = Files.walk(mediaDirectory)) {
            assertThat(files.map(Path::getFileName).map(Path::toString))
                    .noneMatch(name -> name.startsWith(".upload-"));
        }
    }

    @Test
    void inspectsOpensAndDeletesStoredObjects() throws Exception {
        LocalObjectStorage storage = storage();
        String key = "attachments/" + UUID.randomUUID() + ".pdf";
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        storage.upload(new ObjectUploadRequest(key, "application/pdf", content.length), new ByteArrayInputStream(content));

        StoredObject inspected = storage.inspect(key);

        assertThat(inspected.key()).isEqualTo(key);
        assertThat(inspected.contentType()).isEqualTo("application/pdf");
        assertThat(inspected.byteSize()).isEqualTo(content.length);
        try (var input = storage.openStream(key)) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
        storage.delete(key);
        assertThat(Files.exists(mediaDirectory.resolve(key))).isFalse();
        storage.delete(key);
    }

    @Test
    void resolvesLegacyPublicUrl() {
        LocalObjectStorage storage = storage();
        String key = "inline-images/" + UUID.randomUUID() + ".gif";

        assertThat(storage.resolvePublicUrl(key)).hasToString("/api/media/" + key);
    }

    @Test
    void acceptsExistingFlatUuidKeysForLegacyCompatibility() throws Exception {
        LocalObjectStorage storage = storage();
        String key = UUID.randomUUID() + ".png";
        byte[] content = {1};

        storage.upload(new ObjectUploadRequest(key, "image/png", content.length), new ByteArrayInputStream(content));

        assertThat(storage.inspect(key).byteSize()).isEqualTo(1);
    }

    @Test
    void rejectsTraversalAndMalformedKeys() {
        LocalObjectStorage storage = storage();

        assertThatIllegalArgumentException().isThrownBy(() -> storage.inspect("../private.png"))
                .withMessage("Invalid storage key");
        assertThatIllegalArgumentException().isThrownBy(() -> storage.resolvePublicUrl("inline-images/not-a-uuid.png"))
                .withMessage("Invalid storage key");
    }

    @Test
    void reportsAbsentObjectsWithoutLeakingFilesystemPaths() {
        LocalObjectStorage storage = storage();
        String key = "avatars/" + UUID.randomUUID() + ".png";

        assertThatThrownBy(() -> storage.inspect(key))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(key);
    }

    @Test
    void rejectsMismatchedRequestSize() {
        LocalObjectStorage storage = storage();
        String key = "tool-covers/" + UUID.randomUUID() + ".png";

        assertThatIllegalArgumentException().isThrownBy(() -> storage.upload(
                        new ObjectUploadRequest(key, "image/png", 2), new ByteArrayInputStream(new byte[]{1})))
                .withMessage("Uploaded object size does not match declared size");
    }

    private LocalObjectStorage storage() {
        MediaProperties properties = new MediaProperties();
        properties.setDirectory(mediaDirectory);
        return new LocalObjectStorage(properties);
    }
}
