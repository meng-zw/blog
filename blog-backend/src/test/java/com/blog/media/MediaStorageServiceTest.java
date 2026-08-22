package com.blog.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertThat(Files.readAllBytes(mediaDirectory.resolve(stored.getStorageKey()))).isEqualTo(png(3, 2));
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

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
