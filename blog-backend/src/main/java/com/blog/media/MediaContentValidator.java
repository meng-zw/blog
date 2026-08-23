package com.blog.media;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Validates client declarations and authoritative object content for media uploads.
 */
@Component
public class MediaContentValidator {
    private static final String PNG = "image/png";
    private static final String JPEG = "image/jpeg";
    private static final String GIF = "image/gif";
    private static final String PDF = "application/pdf";
    private static final String ZIP = "application/zip";
    private static final String TEXT = "text/plain";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private static final Map<String, FileType> IMAGE_TYPES = Map.of(
            PNG, new FileType("png", "png", new byte[][]{{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}}),
            JPEG, new FileType("jpg", "jpeg", new byte[][]{{(byte) 0xff, (byte) 0xd8, (byte) 0xff}, {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0}, {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe1}}),
            GIF, new FileType("gif", "gif", new byte[][]{{'G', 'I', 'F', '8', '7', 'a'}, {'G', 'I', 'F', '8', '9', 'a'}}));

    private static final Map<String, FileType> ATTACHMENT_TYPES = Map.of(
            PDF, new FileType("pdf", null, new byte[][]{{'%', 'P', 'D', 'F', '-'}}),
            ZIP, new FileType("zip", null, zipSignatures()),
            TEXT, new FileType("txt", null, null),
            DOCX, new FileType("docx", null, zipSignatures()),
            XLSX, new FileType("xlsx", null, zipSignatures()),
            PPTX, new FileType("pptx", null, zipSignatures()));

    private final MediaProperties properties;

    public MediaContentValidator(MediaProperties properties) {
        this.properties = properties;
    }

    public void validateDeclaration(MediaPurpose purpose, String filename, String contentType, long byteSize) {
        FileType type = validatePurposeAndContentType(purpose, contentType);
        validateFilename(filename);
        validateExtension(filename, type);
        if (byteSize <= 0) {
            throw new IllegalArgumentException("Byte size must be positive");
        }
        long maximum = maximumBytes(purpose, normalizeContentType(contentType));
        if (byteSize > maximum) {
            throw new IllegalArgumentException(sizeMessage(purpose, normalizeContentType(contentType), maximum));
        }
    }

    public ValidatedContent validateStoredContent(MediaPurpose purpose, String contentType, InputStream content) {
        FileType type = validatePurposeAndContentType(purpose, contentType);
        if (content == null) {
            throw new IllegalArgumentException("Stored media content is required");
        }
        String normalizedContentType = normalizeContentType(contentType);
        byte[] bytes = readContent(content, maximumBytes(purpose, normalizedContentType), purpose, normalizedContentType);
        if (type.signatures() != null && !hasAllowedSignature(bytes, type.signatures())) {
            throw new IllegalArgumentException(purpose == MediaPurpose.ATTACHMENT
                    ? "Attachment signature does not match its declared type"
                    : "Image signature does not match its declared type");
        }
        if (purpose == MediaPurpose.ATTACHMENT) {
            return ValidatedContent.attachment();
        }
        return validateImage(bytes, type);
    }

    private FileType validatePurposeAndContentType(MediaPurpose purpose, String contentType) {
        if (purpose == null) {
            throw new IllegalArgumentException("Media purpose is required");
        }
        String normalizedContentType = normalizeContentType(contentType);
        Map<String, FileType> allowedTypes = purpose == MediaPurpose.ATTACHMENT ? ATTACHMENT_TYPES : IMAGE_TYPES;
        FileType type = allowedTypes.get(normalizedContentType);
        if (type == null) {
            throw new IllegalArgumentException(purpose == MediaPurpose.ATTACHMENT
                    ? "Only PDF, ZIP, TXT, DOCX, XLSX, or PPTX attachments are allowed"
                    : "Only PNG, JPEG, or GIF uploads are allowed");
        }
        return type;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }
        int parameters = contentType.indexOf(';');
        return contentType.substring(0, parameters < 0 ? contentType.length() : parameters).trim().toLowerCase(Locale.ROOT);
    }

    private static void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")
                || filename.contains("..") || filename.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid filename");
        }
    }

    private static void validateExtension(String filename, FileType type) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        boolean jpegExtension = type.extension().equals("jpg") && extension.equals("jpeg");
        if (!extension.equals(type.extension()) && !jpegExtension) {
            throw new IllegalArgumentException("Filename extension does not match content type");
        }
    }

    private long maximumBytes(MediaPurpose purpose, String contentType) {
        if (purpose != MediaPurpose.ATTACHMENT) {
            return properties.getMaxBytes();
        }
        return contentType.equals(ZIP) ? properties.getMaxZipAttachmentBytes() : properties.getMaxAttachmentBytes();
    }

    private static String sizeMessage(MediaPurpose purpose, String contentType, long maximumBytes) {
        String subject = purpose != MediaPurpose.ATTACHMENT ? "Image"
                : contentType.equals(ZIP) ? "ZIP attachments" : "Attachment";
        return subject + " must not exceed " + formatMebibytes(maximumBytes);
    }

    private static String formatMebibytes(long bytes) {
        long mebibyte = 1024L * 1024L;
        return bytes % mebibyte == 0 ? (bytes / mebibyte) + " MiB" : bytes + " bytes";
    }

    private byte[] readContent(InputStream content, long maximumBytes, MediaPurpose purpose, String contentType) {
        try (InputStream input = content; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new IllegalArgumentException(sizeMessage(purpose, contentType, maximumBytes));
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read stored media content", exception);
        }
    }

    private ValidatedContent validateImage(byte[] bytes, FileType expectedType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Image cannot be decoded");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Image cannot be decoded");
            }
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase(expectedType.imageIoFormat())) {
                    throw new IllegalArgumentException("Image format does not match its declared type");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                if (reader.read(0) == null) {
                    throw new IllegalArgumentException("Image cannot be decoded");
                }
                return new ValidatedContent(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Image cannot be decoded", exception);
        }
    }

    private void validateDimensions(int width, int height) {
        int maximumDimension = properties.getMaxDimension();
        if (width <= 0 || height <= 0 || width > maximumDimension || height > maximumDimension) {
            throw new IllegalArgumentException("Image dimensions must not exceed " + maximumDimension + " pixels");
        }
    }

    private static boolean hasAllowedSignature(byte[] bytes, byte[][] signatures) {
        for (byte[] signature : signatures) {
            if (bytes.length < signature.length) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < signature.length; index++) {
                if (bytes[index] != signature[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static byte[][] zipSignatures() {
        return new byte[][]{{'P', 'K', 3, 4}, {'P', 'K', 5, 6}, {'P', 'K', 7, 8}};
    }

    public record ValidatedContent(Integer width, Integer height) {
        public static ValidatedContent attachment() {
            return new ValidatedContent(null, null);
        }
    }

    private record FileType(String extension, String imageIoFormat, byte[][] signatures) {
    }
}
