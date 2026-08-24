package com.blog.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaStorageServiceTest {

    @TempDir
    Path mediaDirectory;

    @Test
    void storesValidatedPngWithUuidKeyAndDimensions() throws Exception {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MediaStorageService service = new MediaStorageService(repository, properties());

        MediaAsset stored = service.store(new MockMultipartFile(
                "file", "portrait.png", "image/png", png(3, 2)));

        assertThat(stored.getStorageKey()).matches("[0-9a-f-]{36}\\.png");
        assertThat(stored.getWidth()).isEqualTo(3);
        assertThat(stored.getHeight()).isEqualTo(2);
        assertThat(stored.getProvider()).isEqualTo(StorageProvider.LOCAL);
        assertThat(stored.getBucket()).isEmpty();
        assertThat(stored.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(stored.getPurpose()).isEqualTo(MediaPurpose.INLINE_IMAGE);
        assertThat(stored.getConfirmedAt()).isEqualTo(stored.getCreatedAt());
        assertThat(stored.getUpdatedAt()).isEqualTo(stored.getCreatedAt());
        assertThat(Files.readAllBytes(mediaDirectory.resolve(stored.getStorageKey()))).isEqualTo(png(3, 2));
    }

    @Test
    void storesValidatedJpegWithUuidKeyAndDimensions() throws Exception {
        MediaAssetRepository repository = savingRepository();
        MediaStorageService service = new MediaStorageService(repository, properties());

        MediaAsset stored = service.store(new MockMultipartFile(
                "file", "portrait.jpg", "image/jpeg", image("jpeg", 3, 2)));

        assertThat(stored.getStorageKey()).matches("[0-9a-f-]{36}\\.jpg");
        assertThat(stored.getWidth()).isEqualTo(3);
        assertThat(stored.getHeight()).isEqualTo(2);
    }

    @Test
    void storesValidatedGifWithUuidKeyAndDimensions() throws Exception {
        MediaAssetRepository repository = savingRepository();
        MediaStorageService service = new MediaStorageService(repository, properties());

        MediaAsset stored = service.store(new MockMultipartFile(
                "file", "portrait.gif", "image/gif", image("gif", 3, 2)));

        assertThat(stored.getStorageKey()).matches("[0-9a-f-]{36}\\.gif");
        assertThat(stored.getWidth()).isEqualTo(3);
        assertThat(stored.getHeight()).isEqualTo(2);
    }

    @Test
    void deletesFinalizedFileWhenRepositorySaveFails() throws Exception {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        when(repository.save(any(MediaAsset.class))).thenThrow(new IllegalStateException("database unavailable"));
        MediaStorageService service = new MediaStorageService(repository, properties());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.store(new MockMultipartFile(
                        "file", "portrait.png", "image/png", png(3, 2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        try (var files = Files.list(mediaDirectory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void rejectsPngFilenameContainingHtmlRatherThanImageBytes() {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "not-an-image.png", "image/png", "<html>not an image</html>".getBytes())))
                .withMessageContaining("signature");
    }

    @Test
    void rejectsPngSignatureThatCannotBeDecodedAsAnImage() {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());
        byte[] corruptPng = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "corrupt.png", "image/png", corruptPng)))
                .withMessageContaining("decoded");
    }

    @Test
    void rejectsPngBytesDeclaredAsJpeg() throws Exception {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "portrait.jpg", "image/jpeg", png(1, 1))))
                .withMessageContaining("signature");
    }

    @Test
    void rejectsSvgUpload() {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "vector.svg", "image/svg+xml", "<svg/>".getBytes())))
                .withMessageContaining("PNG, JPEG, or GIF");
    }

    @Test
    void rejectsFilesLargerThanFiveMebibytes() {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());
        byte[] oversized = new byte[(5 * 1024 * 1024) + 1];

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "large.png", "image/png", oversized)))
                .withMessageContaining("5 MiB");
    }

    @Test
    void rejectsImagesWhoseDimensionsExceedSixThousandPixels() throws Exception {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "wide.png", "image/png", png(6001, 1))))
                .withMessageContaining("6000");
    }

    @Test
    void rejectsOversizedDeclaredPngDimensionsBeforePixelDecode() throws Exception {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "declared-large.png", "image/png", pngHeaderWithoutPixels(6001, 6001))))
                .withMessageContaining("6000");
    }

    @Test
    void rejectsFilenameWithPathSeparators() {
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class), properties());

        assertThatIllegalArgumentException().isThrownBy(() -> service.store(new MockMultipartFile(
                "file", "../portrait.png", "image/png", png(1, 1))))
                .withMessageContaining("filename");
    }

    @Test
    void delegatesLegacyUploadToLocalObjectStorage() throws Exception {
        MediaAssetRepository repository = savingRepository();
        MediaProperties properties = properties();
        com.blog.media.storage.LocalObjectStorage storage = mock(com.blog.media.storage.LocalObjectStorage.class);
        byte[] bytes = png(3, 2);
        when(storage.upload(any(com.blog.media.storage.ObjectLocation.class),
                any(com.blog.media.storage.ObjectUploadRequest.class), any(ByteArrayInputStream.class)))
                .thenAnswer(invocation -> {
                    com.blog.media.storage.ObjectUploadRequest request = invocation.getArgument(1);
                    return new com.blog.media.storage.StoredObject(request.objectKey(), request.contentType(),
                            request.byteSize(), "etag");
                });
        MediaStorageService service = new MediaStorageService(repository, new MediaContentValidator(properties), storage);

        service.store(new MockMultipartFile("file", "portrait.png", "image/png", bytes));

        verify(storage).upload(any(com.blog.media.storage.ObjectLocation.class),
                any(com.blog.media.storage.ObjectUploadRequest.class), any(ByteArrayInputStream.class));
    }

    @Test
    void delegatesLegacyReadsToLocalObjectStorage() {
        MediaProperties properties = properties();
        com.blog.media.storage.LocalObjectStorage storage = mock(com.blog.media.storage.LocalObjectStorage.class);
        String key = "123e4567-e89b-12d3-a456-426614174000.png";
        Path expected = mediaDirectory.resolve(key);
        when(storage.loadPath(key)).thenReturn(expected);
        MediaStorageService service = new MediaStorageService(mock(MediaAssetRepository.class),
                new MediaContentValidator(properties), storage);

        assertThat(service.load(key)).isEqualTo(expected);

        verify(storage).loadPath(key);
    }

    @Test
    void resolvesOverlappingStorageKeysFromLocalProviderOnly() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        String key = "123e4567-e89b-12d3-a456-426614174000.png";
        MediaAsset local = mediaAsset(StorageProvider.LOCAL, "", key);
        local.setStatus(MediaStatus.READY);
        MediaAsset r2 = mediaAsset(StorageProvider.R2, "blog-media", key);
        when(repository.findByStorageKey(key)).thenReturn(Optional.of(r2));
        when(repository.findByProviderAndBucketAndStorageKey(StorageProvider.LOCAL, "", key))
                .thenReturn(Optional.of(local));
        MediaStorageService service = new MediaStorageService(repository, properties());

        assertThat(service.findByStorageKey(key)).isSameAs(local);

        verify(repository).findByProviderAndBucketAndStorageKey(StorageProvider.LOCAL, "", key);
        verify(repository, never()).findByStorageKey(key);
    }

    @Test
    void legacyPublicLookupRejectsMediaThatIsNotReady() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        String key = "123e4567-e89b-12d3-a456-426614174000.png";
        MediaAsset deleting = mediaAsset(StorageProvider.LOCAL, "", key);
        deleting.setStatus(MediaStatus.DELETING);
        when(repository.findByProviderAndBucketAndStorageKey(StorageProvider.LOCAL, "", key))
                .thenReturn(Optional.of(deleting));

        assertThatThrownBy(() -> new MediaStorageService(repository, properties()).findByStorageKey(key))
                .isInstanceOf(com.blog.shared.error.ResourceNotFoundException.class);
    }

    private MediaProperties properties() {
        MediaProperties properties = new MediaProperties();
        properties.setDirectory(mediaDirectory);
        return properties;
    }

    private static MediaAssetRepository savingRepository() {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        when(repository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    private static MediaAsset mediaAsset(StorageProvider provider, String bucket, String storageKey) {
        MediaAsset asset = new MediaAsset();
        asset.setProvider(provider);
        asset.setBucket(bucket);
        asset.setStorageKey(storageKey);
        return asset;
    }

    private static byte[] png(int width, int height) throws Exception {
        return image("png", width, height);
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        int type = format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, type);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private static byte[] pngHeaderWithoutPixels(int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(ihdr)) {
            data.writeInt(width);
            data.writeInt(height);
            data.writeByte(8);
            data.writeByte(2);
            data.writeByte(0);
            data.writeByte(0);
            data.writeByte(0);
        }
        writePngChunk(output, "IHDR", ihdr.toByteArray());
        writePngChunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream output, String type, byte[] data) throws Exception {
        try (DataOutputStream stream = new DataOutputStream(output)) {
            stream.writeInt(data.length);
            byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            stream.write(typeBytes);
            stream.write(data);
            CRC32 crc = new CRC32();
            crc.update(typeBytes);
            crc.update(data);
            stream.writeInt((int) crc.getValue());
        }
    }
}
