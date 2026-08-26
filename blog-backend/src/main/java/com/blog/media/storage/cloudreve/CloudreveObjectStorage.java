package com.blog.media.storage.cloudreve;

import com.blog.media.StorageProvider;
import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectStorageException;
import com.blog.media.storage.ObjectUploadRequest;
import com.blog.media.storage.StorageCapabilities;
import com.blog.media.storage.StoredObject;
import com.blog.media.storage.UploadTicket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cloudreve proxy adapter backed by the authenticated v4 file client. */
@Component
@Conditional(CloudreveConfiguration.CloudreveRequiredConfigurationCondition.class)
public final class CloudreveObjectStorage implements ObjectStorage {
    private static final Pattern NEW_OBJECT_KEY = Pattern.compile(
            "(avatars|article-covers|topic-covers|tool-covers|inline-images|attachments)/"
                    + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(?:png|jpg|gif|pdf|zip|txt|docx|xlsx|pptx))");
    private static final Pattern STORED_OBJECT_KEY = Pattern.compile(
            "(?:avatars|article-covers|topic-covers|tool-covers|inline-images|attachments)/"
                    + "\\d{4}/(?:0[1-9]|1[0-2])/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(?:png|jpg|gif|pdf|zip|txt|docx|xlsx|pptx)");

    private final CloudreveFileClient client;
    private final Clock clock;
    private final String rootPath;
    private final String rootIdentity;

    @Autowired
    public CloudreveObjectStorage(CloudreveProperties properties, CloudreveFileClient client) {
        this(properties, client, Clock.systemUTC());
    }

    CloudreveObjectStorage(CloudreveProperties properties, CloudreveFileClient client, Clock clock) {
        CloudreveProperties requiredProperties = Objects.requireNonNull(properties, "Cloudreve properties are required");
        this.client = Objects.requireNonNull(client, "Cloudreve file client is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.rootPath = requireRootPath(requiredProperties.getRootPath());
        this.rootIdentity = logicalUri(rootPath);
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.CLOUDREVE;
    }

    @Override
    public StorageCapabilities capabilities() {
        return new StorageCapabilities(false, true);
    }

    @Override
    public ObjectLocation locationForNewObject(String objectKey) {
        Matcher key = NEW_OBJECT_KEY.matcher(objectKey == null ? "" : objectKey);
        if (!key.matches()) throw new IllegalArgumentException("Invalid Cloudreve object key");
        YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        String storedKey = key.group(1) + "/" + month.getYear() + "/"
                + String.format(java.util.Locale.ROOT, "%02d", month.getMonthValue()) + "/" + key.group(2);
        return new ObjectLocation(StorageProvider.CLOUDREVE, rootIdentity, storedKey);
    }

    @Override
    public UploadTicket createDirectUpload(ObjectLocation location, ObjectUploadRequest request) {
        throw new UnsupportedOperationException("Cloudreve object storage uses proxy uploads");
    }

    @Override
    public StoredObject upload(ObjectLocation location, ObjectUploadRequest request, InputStream content) {
        String key = validatedKey(location);
        if (request == null || !key.equals(request.objectKey())) {
            throw new IllegalArgumentException("Object request key does not match its location");
        }
        try {
            return storedObject(key, client.upload(key, request, content));
        } catch (CloudreveApiException failure) {
            if (failure.kind() == CloudreveApiException.Kind.CONFLICT) {
                return recoverExistingUpload(key, request, failure);
            }
            throw translate(failure);
        }
    }

    @Override
    public StoredObject inspect(ObjectLocation location) {
        String key = validatedKey(location);
        try {
            return storedObject(key, client.inspect(key));
        } catch (CloudreveApiException failure) {
            throw translate(failure);
        }
    }

    @Override
    public InputStream openStream(ObjectLocation location) throws IOException {
        String key = validatedKey(location);
        try {
            return client.open(key);
        } catch (CloudreveApiException failure) {
            throw translate(failure);
        }
    }

    @Override
    public URI resolvePublicUrl(ObjectLocation location) {
        validatedKey(location);
        throw new UnsupportedOperationException("Cloudreve media is available only through the stable proxy URL");
    }

    @Override
    public void delete(ObjectLocation location) throws IOException {
        String key = validatedKey(location);
        try {
            client.delete(key);
        } catch (CloudreveApiException failure) {
            if (failure.kind() != CloudreveApiException.Kind.NOT_FOUND) throw translate(failure);
        }
    }

    private StoredObject storedObject(String expectedKey, CloudreveFileMetadata metadata) {
        if (metadata == null || !logicalUri(rootPath + "/" + expectedKey).equals(metadata.path())) {
            throw ObjectStorageException.transientFailure("Cloudreve returned invalid media metadata", null);
        }
        return new StoredObject(expectedKey, metadata.contentType(), metadata.byteSize(), metadata.primaryEntity());
    }

    private StoredObject recoverExistingUpload(String key, ObjectUploadRequest request,
                                               CloudreveApiException originalConflict) {
        try {
            StoredObject existing = storedObject(key, client.inspect(key));
            if (existing.byteSize() == request.byteSize()
                    && existing.contentType().equalsIgnoreCase(request.contentType())) {
                return existing;
            }
        } catch (RuntimeException recoveryFailure) {
            originalConflict.addSuppressed(recoveryFailure);
        }
        throw ObjectStorageException.transientFailure(
                "Cloudreve upload conflicts with an unexpected media object", originalConflict);
    }

    private String validatedKey(ObjectLocation location) {
        if (location == null || location.provider() != StorageProvider.CLOUDREVE
                || !rootIdentity.equals(location.bucket()) || !STORED_OBJECT_KEY.matcher(location.objectKey()).matches()) {
            throw new IllegalArgumentException("Invalid Cloudreve object location");
        }
        return location.objectKey();
    }

    private static ObjectStorageException translate(CloudreveApiException failure) {
        if (failure.kind() == CloudreveApiException.Kind.NOT_FOUND) {
            return ObjectStorageException.notFound("Cloudreve media object was not found", failure);
        }
        return ObjectStorageException.transientFailure("Cloudreve media storage is temporarily unavailable", failure);
    }

    private static String requireRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank() || !rootPath.startsWith("/") || rootPath.contains("\\")
                || rootPath.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Cloudreve root path is invalid");
        }
        String normalized = rootPath.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        for (String segment : normalized.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Cloudreve root path is invalid");
            }
        }
        return normalized;
    }

    private static String logicalUri(String absolutePath) {
        try {
            return new URI("cloudreve", "my", absolutePath, null).toASCIIString();
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Cloudreve root path is invalid", failure);
        }
    }
}
