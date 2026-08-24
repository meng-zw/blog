package com.blog.media.storage.r2;

import com.blog.media.StorageProvider;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectLocation;
import com.blog.media.storage.ObjectUploadRequest;
import com.blog.media.storage.StorageCapabilities;
import com.blog.media.storage.StoredObject;
import com.blog.media.storage.UploadMode;
import com.blog.media.storage.UploadTicket;
import com.blog.media.storage.ObjectStorageException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Cloudflare R2 implementation backed by the S3-compatible AWS SDK v2 client. */
public class R2ObjectStorage implements ObjectStorage {
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client client;
    private final S3Presigner presigner;
    private final R2Properties properties;
    private final Clock clock;

    public R2ObjectStorage(S3Client client, S3Presigner presigner, R2Properties properties) {
        this(client, presigner, properties, Clock.systemUTC());
    }

    R2ObjectStorage(S3Client client, S3Presigner presigner, R2Properties properties, Clock clock) {
        this.client = java.util.Objects.requireNonNull(client, "R2 S3 client is required");
        this.presigner = java.util.Objects.requireNonNull(presigner, "R2 S3 presigner is required");
        this.properties = java.util.Objects.requireNonNull(properties, "R2 properties are required");
        this.clock = java.util.Objects.requireNonNull(clock, "Clock is required");
        properties.validate();
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.R2;
    }

    @Override
    public StorageCapabilities capabilities() {
        return new StorageCapabilities(true, true);
    }

    @Override
    public ObjectLocation locationForNewObject(String objectKey) {
        properties.validate();
        return new ObjectLocation(StorageProvider.R2, properties.getBucket(), requireObjectKey(objectKey));
    }

    @Override
    public UploadTicket createDirectUpload(ObjectLocation location, ObjectUploadRequest request) {
        validateUploadLocation(location, request.objectKey());
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.getUploadUrlTtl())
                .putObjectRequest(putRequest(location, request))
                .build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(presignRequest);
        Instant expiresAt = clock.instant().plus(properties.getUploadUrlTtl());
        return new UploadTicket(UploadMode.DIRECT, "PUT", URI.create(signed.url().toString()), requiredHeaders(request), expiresAt);
    }

    @Override
    public StoredObject upload(ObjectLocation location, ObjectUploadRequest request, InputStream content) {
        throw new UnsupportedOperationException("R2 object storage accepts browser direct uploads only");
    }

    @Override
    public StoredObject inspect(ObjectLocation location) {
        String objectKey = readableKey(location);
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(location.bucket()).key(objectKey).build());
            return new StoredObject(objectKey, requireContentType(response.contentType(), objectKey), response.contentLength(), response.eTag());
        } catch (SdkException exception) {
            throw translateFailure(objectKey, exception);
        }
    }

    @Override
    public InputStream openStream(ObjectLocation location) throws IOException {
        String objectKey = readableKey(location);
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
                    .bucket(location.bucket()).key(objectKey).build(), ResponseTransformer.toInputStream());
            return stream;
        } catch (SdkException exception) {
            throw translateFailure(objectKey, exception);
        }
    }

    @Override
    public URI resolvePublicUrl(ObjectLocation location) {
        String objectKey = readableKey(location);
        String encodedObjectPath = encodePath(requireObjectKey(objectKey));
        URI base = properties.publicBaseUri(location.bucket());
        String basePath = base.getRawPath() == null ? "" : base.getRawPath().replaceAll("/+$", "");
        String publicUrl = base.getScheme() + "://" + base.getRawAuthority() + basePath + "/" + encodedObjectPath;
        return URI.create(publicUrl);
    }

    @Override
    public void delete(ObjectLocation location) throws IOException {
        String objectKey = readableKey(location);
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(location.bucket()).key(objectKey).build());
        } catch (SdkException exception) {
            if (!(exception instanceof S3Exception s3) || !isMissing(s3)) {
                throw ObjectStorageException.transientFailure("Unable to delete R2 object", exception);
            }
        }
    }

    private PutObjectRequest putRequest(ObjectLocation location, ObjectUploadRequest request) {
        return PutObjectRequest.builder()
                .bucket(location.bucket())
                .key(request.objectKey())
                .contentType(request.contentType())
                .cacheControl(IMMUTABLE_CACHE_CONTROL)
                .contentDisposition(contentDisposition(request.objectKey()))
                .build();
    }

    private static Map<String, String> requiredHeaders(ObjectUploadRequest request) {
        return Map.of(
                "Content-Type", request.contentType(),
                "Cache-Control", IMMUTABLE_CACHE_CONTROL,
                "Content-Disposition", contentDisposition(request.objectKey()));
    }

    private static String contentDisposition(String objectKey) {
        return objectKey.startsWith("attachments/") ? "attachment" : "inline";
    }

    private static String requireObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return objectKey;
    }

    private String readableKey(ObjectLocation location) {
        validateProvider(location);
        properties.publicBaseUri(location.bucket());
        return requireObjectKey(location.objectKey());
    }

    private void validateUploadLocation(ObjectLocation location, String expectedKey) {
        validateProvider(location);
        if (!properties.getBucket().equals(location.bucket())) {
            throw new IllegalArgumentException("New R2 uploads must use the configured upload bucket");
        }
        if (!location.objectKey().equals(expectedKey)) {
            throw new IllegalArgumentException("Object request key does not match its location");
        }
        requireObjectKey(location.objectKey());
    }

    private static void validateProvider(ObjectLocation location) {
        if (location == null || location.provider() != StorageProvider.R2 || location.bucket().isBlank()) {
            throw new IllegalArgumentException("Invalid R2 object location");
        }
    }

    private static String requireContentType(String contentType, String objectKey) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Stored object is missing content type: " + objectKey);
        }
        return contentType;
    }

    private static ObjectStorageException translateFailure(String objectKey, SdkException exception) {
        if (exception instanceof S3Exception s3 && isMissing(s3)) {
            return ObjectStorageException.notFound("Media object not found: " + objectKey, exception);
        }
        return ObjectStorageException.transientFailure("Unable to access R2 object: " + objectKey, exception);
    }

    private static boolean isMissing(S3Exception exception) {
        return exception instanceof NoSuchKeyException || exception.statusCode() == 404 || "NoSuchKey".equals(exception.awsErrorDetails() == null
                ? null : exception.awsErrorDetails().errorCode());
    }

    private static String encodePath(String objectKey) {
        try {
            return new URI(null, null, "/" + objectKey, null).toASCIIString().substring(1);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid storage key", exception);
        }
    }
}
