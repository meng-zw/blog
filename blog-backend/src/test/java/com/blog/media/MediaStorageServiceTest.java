package com.blog.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
