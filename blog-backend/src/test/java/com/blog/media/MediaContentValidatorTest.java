package com.blog.media;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MediaContentValidatorTest {

    private final MediaContentValidator validator = new MediaContentValidator(properties());

    @Test
    void acceptsAllowedImageDeclarationsForImagePurposes() {
        validator.validateDeclaration(MediaPurpose.AVATAR, "avatar.png", "image/png", 1024);
        validator.validateDeclaration(MediaPurpose.ARTICLE_COVER, "cover.jpg", "image/jpeg", 1024);
        validator.validateDeclaration(MediaPurpose.INLINE_IMAGE, "animation.gif", "image/gif", 1024);
    }

    @Test
    void acceptsAllowedAttachmentDeclarationsWithPurposeSpecificLimits() {
        validator.validateDeclaration(MediaPurpose.ATTACHMENT, "guide.pdf", "application/pdf", 20L * 1024 * 1024);
        validator.validateDeclaration(MediaPurpose.ATTACHMENT, "archive.zip", "application/zip", 50L * 1024 * 1024);
        validator.validateDeclaration(MediaPurpose.ATTACHMENT, "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1024);
    }

    @Test
    void rejectsImageMimeTypeForAttachments() {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateDeclaration(
                        MediaPurpose.ATTACHMENT, "photo.png", "image/png", 1024))
                .withMessage("Only PDF, ZIP, TXT, DOCX, XLSX, or PPTX attachments are allowed");
    }

    @Test
    void rejectsAttachmentMimeTypeForImagePurposes() {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateDeclaration(
                        MediaPurpose.TOPIC_COVER, "guide.pdf", "application/pdf", 1024))
                .withMessage("Only PNG, JPEG, or GIF uploads are allowed");
    }

    @Test
    void rejectsFilesOverTheirPurposeSpecificLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateDeclaration(
                        MediaPurpose.INLINE_IMAGE, "wide.png", "image/png", (5L * 1024 * 1024) + 1))
                .withMessage("Image must not exceed 5 MiB");
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateDeclaration(
                        MediaPurpose.ATTACHMENT, "guide.pdf", "application/pdf", (20L * 1024 * 1024) + 1))
                .withMessage("Attachment must not exceed 20 MiB");
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateDeclaration(
                        MediaPurpose.ATTACHMENT, "archive.zip", "application/zip", (50L * 1024 * 1024) + 1))
                .withMessage("ZIP attachments must not exceed 50 MiB");
    }

    @Test
    void rejectsPathLikeFilename() {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateDeclaration(
                        MediaPurpose.INLINE_IMAGE, "../avatar.png", "image/png", 1024))
                .withMessage("Invalid filename");
    }

    @Test
    void rejectsFilenameWithAnExtensionThatDisagreesWithItsContentType() {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateDeclaration(
                        MediaPurpose.ATTACHMENT, "guide.zip", "application/pdf", 1024))
                .withMessage("Filename extension does not match content type");
    }

    @Test
    void validatesPngJpegAndGifSignaturesAndImageDecoding() throws Exception {
        MediaContentValidator.ValidatedContent png = validator.validateStoredContent(
                MediaPurpose.INLINE_IMAGE, "image/png", new ByteArrayInputStream(image("png", 3, 2)));
        MediaContentValidator.ValidatedContent jpeg = validator.validateStoredContent(
                MediaPurpose.INLINE_IMAGE, "image/jpeg", new ByteArrayInputStream(image("jpeg", 4, 2)));
        MediaContentValidator.ValidatedContent gif = validator.validateStoredContent(
                MediaPurpose.INLINE_IMAGE, "image/gif", new ByteArrayInputStream(image("gif", 2, 5)));

        assertThat(png.width()).isEqualTo(3);
        assertThat(png.height()).isEqualTo(2);
        assertThat(jpeg.width()).isEqualTo(4);
        assertThat(jpeg.height()).isEqualTo(2);
        assertThat(gif.width()).isEqualTo(2);
        assertThat(gif.height()).isEqualTo(5);
    }

    @Test
    void rejectsAnImageSignatureThatDoesNotMatchItsDeclaredType() {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateStoredContent(
                        MediaPurpose.INLINE_IMAGE, "image/jpeg", new ByteArrayInputStream(pngSignature())))
                .withMessage("Image signature does not match its declared type");
    }

    @Test
    void rejectsAnImageThatCannotBeDecodedAfterItsSignatureIsAccepted() {
        byte[] corruptPng = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};

        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateStoredContent(
                        MediaPurpose.INLINE_IMAGE, "image/png", new ByteArrayInputStream(corruptPng)))
                .withMessage("Image cannot be decoded");
    }

    @Test
    void rejectsAnImageWhoseDimensionsExceedTheConfiguredLimit() throws Exception {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateStoredContent(
                        MediaPurpose.INLINE_IMAGE, "image/png", new ByteArrayInputStream(image("png", 6001, 1))))
                .withMessage("Image dimensions must not exceed 6000 pixels");
    }

    @Test
    void validatesPdfAndZipBasedOfficeAttachmentSignatures() {
        assertThat(validator.validateStoredContent(MediaPurpose.ATTACHMENT, "application/pdf",
                new ByteArrayInputStream("%PDF-1.7".getBytes()))).isEqualTo(MediaContentValidator.ValidatedContent.attachment());
        assertThat(validator.validateStoredContent(MediaPurpose.ATTACHMENT,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new ByteArrayInputStream(new byte[]{'P', 'K', 3, 4})))
                .isEqualTo(MediaContentValidator.ValidatedContent.attachment());
    }

    @Test
    void rejectsAnAttachmentWhoseSignatureDoesNotMatchItsDeclaredType() {
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateStoredContent(
                        MediaPurpose.ATTACHMENT, "application/pdf", new ByteArrayInputStream(new byte[]{'P', 'K', 3, 4})))
                .withMessage("Attachment signature does not match its declared type");
    }

    @Test
    void limitsProxyStreamsAtAttachmentAndZipBoundariesWithoutAllocatingFullFiles() throws Exception {
        try (InputStream pdfAtLimit = validator.limitProxyUpload(MediaPurpose.ATTACHMENT, "application/pdf",
                new RepeatingInputStream(20L * 1024 * 1024))) {
            assertThat(pdfAtLimit.transferTo(java.io.OutputStream.nullOutputStream())).isEqualTo(20L * 1024 * 1024);
        }
        try (InputStream zipAtLimit = validator.limitProxyUpload(MediaPurpose.ATTACHMENT, "application/zip",
                new RepeatingInputStream(50L * 1024 * 1024))) {
            assertThat(zipAtLimit.transferTo(java.io.OutputStream.nullOutputStream())).isEqualTo(50L * 1024 * 1024);
        }
        InputStream tooLargePdf = validator.limitProxyUpload(MediaPurpose.ATTACHMENT, "application/pdf",
                new RepeatingInputStream((20L * 1024 * 1024) + 1));
        assertThatIllegalArgumentException().isThrownBy(() -> tooLargePdf.transferTo(java.io.OutputStream.nullOutputStream()))
                .withMessage("Attachment must not exceed 20 MiB");
    }

    private static MediaProperties properties() {
        return new MediaProperties();
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        int type = format.equals("jpeg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, type);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private static byte[] pngSignature() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;

        private RepeatingInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int read = (int) Math.min(remaining, length);
            java.util.Arrays.fill(buffer, offset, offset + read, (byte) 'x');
            remaining -= read;
            return read;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 'x';
        }
    }
}
