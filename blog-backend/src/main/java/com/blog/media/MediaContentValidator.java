package com.blog.media;

import com.blog.media.storage.ObjectStorageException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long MAX_ARCHIVE_EXPANDED_BYTES = 100L * 1024 * 1024;

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
            if (normalizedContentType.equals(ZIP) || normalizedContentType.equals(DOCX)
                    || normalizedContentType.equals(XLSX) || normalizedContentType.equals(PPTX)) {
                validateZipPackage(bytes, requiredOfficeEntries(normalizedContentType));
            }
            return ValidatedContent.attachment();
        }
        return validateImage(bytes, type);
    }

    /**
     * Applies the same purpose-specific size ceiling to a streaming proxy upload without buffering it in memory.
     * The returned stream reads one extra byte after the limit to distinguish an exact-limit file from an oversized one.
     */
    public InputStream limitProxyUpload(MediaPurpose purpose, String contentType, InputStream content) {
        validatePurposeAndContentType(purpose, contentType);
        if (content == null) {
            throw new IllegalArgumentException("Uploaded media content is required");
        }
        String normalizedContentType = normalizeContentType(contentType);
        return new LimitedInputStream(content, maximumBytes(purpose, normalizedContentType),
                sizeMessage(purpose, normalizedContentType, maximumBytes(purpose, normalizedContentType)));
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
            throw ObjectStorageException.transientFailure("Unable to read stored media content", exception);
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

    private static Set<String> requiredOfficeEntries(String contentType) {
        return switch (contentType) {
            case DOCX -> Set.of("[Content_Types].xml", "word/document.xml");
            case XLSX -> Set.of("[Content_Types].xml", "xl/workbook.xml");
            case PPTX -> Set.of("[Content_Types].xml", "ppt/presentation.xml");
            default -> Set.of();
        };
    }

    private static void validateZipPackage(byte[] bytes, Set<String> requiredEntries) {
        Set<String> names = new LinkedHashSet<>();
        long expandedBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("Attachment archive contains too many entries");
                }
                String name = entry.getName();
                if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                        || name.contains("../") || name.contains("..\\") || name.length() > 1024) {
                    throw new IllegalArgumentException("Attachment archive contains an unsafe entry name");
                }
                if (!names.add(name)) {
                    throw new IllegalArgumentException("Attachment archive contains duplicate entries");
                }
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > MAX_ARCHIVE_EXPANDED_BYTES) {
                        throw new IllegalArgumentException("Attachment archive expands beyond the safe limit");
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Attachment ZIP package cannot be decoded", exception);
        }
        if (entries == 0) {
            throw new IllegalArgumentException("Attachment ZIP package cannot be decoded");
        }
        if (!names.containsAll(requiredEntries)) {
            throw new IllegalArgumentException("Office attachment package is missing required entries");
        }
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

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximumBytes;
        private final String oversizeMessage;
        private long consumed;

        private LimitedInputStream(InputStream input, long maximumBytes, String oversizeMessage) {
            super(input);
            this.maximumBytes = maximumBytes;
            this.oversizeMessage = oversizeMessage;
        }

        @Override
        public int read() throws IOException {
            if (consumed >= maximumBytes) {
                return readPastLimit();
            }
            int value = in.read();
            if (value != -1) {
                consumed++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (consumed >= maximumBytes) {
                return readPastLimit();
            }
            int allowed = (int) Math.min(length, maximumBytes - consumed);
            int read = in.read(buffer, offset, allowed);
            if (read != -1) {
                consumed += read;
            }
            return read;
        }

        private int readPastLimit() throws IOException {
            if (in.read() == -1) {
                return -1;
            }
            throw new IllegalArgumentException(oversizeMessage);
        }
    }
}
