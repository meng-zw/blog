package com.blog.media;

import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaStorageService {
    private static final Map<String, ImageType> ALLOWED_TYPES = Map.of(
            "image/png", new ImageType("png", "png", new byte[][]{{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}}),
            "image/jpeg", new ImageType("jpg", "jpeg", new byte[][]{{(byte) 0xff, (byte) 0xd8, (byte) 0xff}}),
            "image/gif", new ImageType("gif", "gif", new byte[][]{{'G', 'I', 'F', '8', '7', 'a'}, {'G', 'I', 'F', '8', '9', 'a'}}));

    private final MediaAssetRepository repository;
    private final MediaProperties properties;

    public MediaStorageService(MediaAssetRepository repository, MediaProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public MediaAsset store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("An image file is required");
        }
        String filename = validateFilename(file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        ImageType imageType = ALLOWED_TYPES.get(contentType);
        if (imageType == null) {
            throw new IllegalArgumentException("Only PNG, JPEG, or GIF uploads are allowed");
        }
        validateExtension(filename, imageType);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded image", exception);
        }
        if (bytes.length > properties.getMaxBytes()) {
            throw new IllegalArgumentException("Image must not exceed 5 MiB");
        }
        if (!hasAllowedSignature(bytes, imageType.signatures())) {
            throw new IllegalArgumentException("Image signature does not match its declared type");
        }
        BufferedImage image = decode(bytes, imageType);
        if (image.getWidth() > properties.getMaxDimension() || image.getHeight() > properties.getMaxDimension()) {
            throw new IllegalArgumentException("Image dimensions must not exceed 6000 pixels");
        }

        String storageKey = UUID.randomUUID() + "." + imageType.extension();
        writeSafely(storageKey, bytes);
        MediaAsset asset = new MediaAsset();
        asset.setStorageKey(storageKey);
        asset.setOriginalFilename(filename);
        asset.setContentType(contentType);
        asset.setByteSize(bytes.length);
        asset.setWidth(image.getWidth());
        asset.setHeight(image.getHeight());
        asset.setCreatedAt(Instant.now());
        return repository.save(asset);
    }

    public Path load(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-f-]{36}\\.(png|jpg|gif)")) {
            throw new ResourceNotFoundException("Media asset", storageKey == null ? "unknown" : storageKey);
        }
        Path root = storageRoot();
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Media asset", storageKey);
        }
        return path;
    }

    public MediaAsset findByStorageKey(String storageKey) {
        return repository.findByStorageKey(storageKey)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", storageKey));
    }

    private String validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        return filename;
    }

    private static void validateExtension(String filename, ImageType type) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        boolean jpegExtension = type.extension().equals("jpg") && extension.equals("jpeg");
        if (!extension.equals(type.extension()) && !jpegExtension) {
            throw new IllegalArgumentException("Filename extension does not match content type");
        }
    }

    private static boolean hasAllowedSignature(byte[] bytes, byte[][] signatures) {
        for (byte[] signature : signatures) {
            if (bytes.length >= signature.length) {
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
        }
        return false;
    }

    private static BufferedImage decode(byte[] bytes, ImageType expectedType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
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
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("Image cannot be decoded");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Image cannot be decoded", exception);
        }
    }

    private void writeSafely(String storageKey, byte[] bytes) {
        Path root = storageRoot();
        Path destination = root.resolve(storageKey).normalize();
        if (!destination.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        try {
            Files.createDirectories(root);
            Path temporary = Files.createTempFile(root, ".upload-", ".tmp");
            try {
                Files.write(temporary, bytes);
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to store uploaded image", exception);
        }
    }

    private Path storageRoot() {
        return properties.getDirectory().toAbsolutePath().normalize();
    }

    private record ImageType(String extension, String imageIoFormat, byte[][] signatures) {
    }
}
