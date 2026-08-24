package com.blog.media.storage.r2;

import com.blog.media.StorageProvider;
import com.blog.media.storage.ObjectStorage;
import com.blog.media.storage.ObjectUploadRequest;
import com.blog.media.storage.StorageCapabilities;
import com.blog.media.storage.StoredObject;
import com.blog.media.storage.UploadMode;
import com.blog.media.storage.UploadTicket;
import com.blog.shared.error.ResourceNotFoundException;
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
    public UploadTicket createDirectUpload(ObjectUploadRequest request) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.getUploadUrlTtl())
                .putObjectRequest(putRequest(request))
                .build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(presignRequest);
        Instant expiresAt = clock.instant().plus(properties.getUploadUrlTtl());
        return new UploadTicket(UploadMode.DIRECT, "PUT", URI.create(signed.url().toString()), requiredHeaders(request), expiresAt);
    }

    @Override
    public StoredObject upload(ObjectUploadRequest request, InputStream content) {
        throw new UnsupportedOperationException("R2 object storage accepts browser direct uploads only");
    }

    @Override
    public StoredObject inspect(String objectKey) {
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(requireObjectKey(objectKey)).build());
            return new StoredObject(objectKey, requireContentType(response.contentType(), objectKey), response.contentLength(), response.eTag());
        } catch (S3Exception exception) {
            throw translateMissing(objectKey, exception);
        }
    }

    @Override
    public InputStream openStream(String objectKey) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(requireObjectKey(objectKey)).build(), ResponseTransformer.toInputStream());
            return stream;
        } catch (S3Exception exception) {
            throw translateMissing(objectKey, exception);
        }
    }

    @Override
    public URI resolvePublicUrl(String objectKey) {
        String encodedObjectPath = encodePath(requireObjectKey(objectKey));
        URI base = properties.publicBaseUri();
        String basePath = base.getRawPath() == null ? "" : base.getRawPath().replaceAll("/+$", "");
        String publicUrl = base.getScheme() + "://" + base.getRawAuthority() + basePath + "/" + encodedObjectPath;
        return URI.create(publicUrl);
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(requireObjectKey(objectKey)).build());
        } catch (S3Exception exception) {
            if (!isMissing(exception)) {
                throw exception;
            }
        }
    }

    private PutObjectRequest putRequest(ObjectUploadRequest request) {
        return PutObjectRequest.builder()
                .bucket(properties.getBucket())
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

    private static String requireContentType(String contentType, String objectKey) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Stored object is missing content type: " + objectKey);
        }
        return contentType;
    }

    private static RuntimeException translateMissing(String objectKey, S3Exception exception) {
        if (isMissing(exception)) {
            return new ResourceNotFoundException("Media object", objectKey);
        }
        return exception;
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
