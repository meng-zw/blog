package com.blog.media.storage;

import com.blog.media.MediaProperties;
import com.blog.media.StorageProvider;
import com.blog.shared.error.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

/** Local filesystem implementation used for development and backwards-compatible media. */
@Component
public class LocalObjectStorage implements ObjectStorage {
    private static final Pattern OBJECT_KEY = Pattern.compile(
            "(?:(?:avatars|article-covers|topic-covers|tool-covers|inline-images|attachments)/)?"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(?:png|jpg|gif|pdf|zip|txt|docx|xlsx|pptx)");
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("gif", "image/gif"),
            Map.entry("pdf", "application/pdf"), Map.entry("zip", "application/zip"), Map.entry("txt", "text/plain"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private final MediaProperties properties;

    public LocalObjectStorage(MediaProperties properties) {
        this.properties = properties;
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.LOCAL;
    }

    @Override
    public StorageCapabilities capabilities() {
        return new StorageCapabilities(false, true);
    }

    @Override
    public ObjectLocation locationForNewObject(String objectKey) {
        validateObjectKey(objectKey);
        return new ObjectLocation(StorageProvider.LOCAL, "", objectKey);
    }

    @Override
    public UploadTicket createDirectUpload(ObjectLocation location, ObjectUploadRequest request) {
        throw new UnsupportedOperationException("Local object storage does not support direct uploads");
    }

    @Override
    public StoredObject upload(ObjectLocation location, ObjectUploadRequest request, InputStream content) throws IOException {
        validateLocation(location, request.objectKey());
        if (content == null) {
            throw new IllegalArgumentException("Object content is required");
        }
        Path destination = objectPath(request.objectKey());
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".upload-", ".tmp");
        try {
            long byteSize;
            String etag;
            try (OutputStream output = Files.newOutputStream(temporary)) {
                MessageDigest digest = sha256();
                try (DigestInputStream input = new DigestInputStream(content, digest)) {
                    byteSize = input.transferTo(output);
                }
                etag = HexFormat.of().formatHex(digest.digest());
            }
            if (byteSize != request.byteSize()) {
                throw new IllegalArgumentException("Uploaded object size does not match declared size");
            }
            moveAtomically(temporary, destination);
            return new StoredObject(request.objectKey(), request.contentType(), byteSize, etag);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public StoredObject inspect(ObjectLocation location) {
        String objectKey = localKey(location);
        Path path = existingObjectPath(objectKey);
        try {
            return new StoredObject(objectKey, contentType(objectKey), Files.size(path), sha256(path));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to inspect media object", exception);
        }
    }

    @Override
    public InputStream openStream(ObjectLocation location) throws IOException {
        String objectKey = localKey(location);
        return Files.newInputStream(existingObjectPath(objectKey));
    }

    @Override
    public URI resolvePublicUrl(ObjectLocation location) {
        String objectKey = localKey(location);
        validateObjectKey(objectKey);
        return URI.create("/api/media/" + objectKey);
    }

    @Override
    public void delete(ObjectLocation location) throws IOException {
        String objectKey = localKey(location);
        Files.deleteIfExists(objectPath(objectKey));
    }

    /** Compatibility bridge for the old controller's FileSystemResource response. */
    @Deprecated(since = "media-v2")
    public Path loadPath(String objectKey) {
        return existingObjectPath(objectKey);
    }

    private Path existingObjectPath(String objectKey) {
        Path path = objectPath(objectKey);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Media asset", objectKey);
        }
        return path;
    }

    private Path objectPath(String objectKey) {
        validateObjectKey(objectKey);
        Path root = properties.getDirectory().toAbsolutePath().normalize();
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static void validateObjectKey(String objectKey) {
        if (objectKey == null || !OBJECT_KEY.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
    }

    private static String localKey(ObjectLocation location) {
        validateLocation(location, location == null ? null : location.objectKey());
        return location.objectKey();
    }

    private static void validateLocation(ObjectLocation location, String expectedKey) {
        if (location == null || location.provider() != StorageProvider.LOCAL || !location.bucket().isEmpty()) {
            throw new IllegalArgumentException("Invalid Local object location");
        }
        if (!location.objectKey().equals(expectedKey)) {
            throw new IllegalArgumentException("Object request key does not match its location");
        }
        validateObjectKey(location.objectKey());
    }

    private static void moveAtomically(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path); DigestInputStream hashed = new DigestInputStream(input, digest)) {
            hashed.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String contentType(String objectKey) {
        return CONTENT_TYPES.get(objectKey.substring(objectKey.lastIndexOf('.') + 1));
    }
}
