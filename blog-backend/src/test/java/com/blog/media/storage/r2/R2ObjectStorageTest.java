package com.blog.media.storage.r2;

import com.blog.media.StorageProvider;
import com.blog.media.storage.ObjectUploadRequest;
import com.blog.media.storage.StoredObject;
import com.blog.media.storage.UploadMode;
import com.blog.media.storage.UploadTicket;
import com.blog.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2ObjectStorageTest {
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void createsTenMinuteDirectPutTicketForTheConfiguredBucketAndObject() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://account.r2.cloudflarestorage.com/blog-media/inline-images/a.png?X-Amz-Signature=token"));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
        R2ObjectStorage storage = storage(mock(S3Client.class), presigner);

        UploadTicket ticket = storage.createDirectUpload(new ObjectUploadRequest("inline-images/a.png", "image/png", 7));

        ArgumentCaptor<PutObjectPresignRequest> request = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(request.capture());
        PutObjectRequest put = request.getValue().putObjectRequest();
        assertThat(storage.provider()).isEqualTo(StorageProvider.R2);
        assertThat(ticket.mode()).isEqualTo(UploadMode.DIRECT);
        assertThat(ticket.method()).isEqualTo("PUT");
        assertThat(ticket.uri()).hasToString("https://account.r2.cloudflarestorage.com/blog-media/inline-images/a.png?X-Amz-Signature=token");
        assertThat(ticket.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(ticket.requiredHeaders()).containsEntry("Content-Type", "image/png")
                .containsEntry("Cache-Control", "public, max-age=31536000, immutable")
                .containsEntry("Content-Disposition", "inline");
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(put.bucket()).isEqualTo("blog-media");
        assertThat(put.key()).isEqualTo("inline-images/a.png");
        assertThat(put.contentType()).isEqualTo("image/png");
        assertThat(put.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(put.contentDisposition()).isEqualTo("inline");
    }

    @Test
    void inspectsTheAuthoritativeHeadMetadataAndMapsMissingObjects() {
        S3Client client = mock(S3Client.class);
        R2ObjectStorage storage = storage(client, mock(S3Presigner.class));
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/png").contentLength(7L).eTag("etag-1").build());

        StoredObject object = storage.inspect("inline-images/a.png");

        ArgumentCaptor<HeadObjectRequest> request = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(client).headObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("blog-media");
        assertThat(request.getValue().key()).isEqualTo("inline-images/a.png");
        assertThat(object).isEqualTo(new StoredObject("inline-images/a.png", "image/png", 7L, "etag-1"));

        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("gone").build());
        assertThatThrownBy(() -> storage.inspect("inline-images/missing.png"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("inline-images/missing.png");
    }

    @Test
    void opensTheObjectValidationStreamFromTheConfiguredBucket() throws Exception {
        S3Client client = mock(S3Client.class);
        R2ObjectStorage storage = storage(client, mock(S3Presigner.class));
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(new byte[]{1, 2, 3})));
        when(client.getObject(any(GetObjectRequest.class), org.mockito.ArgumentMatchers
                .<ResponseTransformer<GetObjectResponse, ResponseInputStream<GetObjectResponse>>>any()))
                .thenReturn(stream);

        try (var content = storage.openStream("inline-images/a.png")) {
            assertThat(content.readAllBytes()).containsExactly(1, 2, 3);
        }

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(client).getObject(request.capture(), org.mockito.ArgumentMatchers
                .<ResponseTransformer<GetObjectResponse, ResponseInputStream<GetObjectResponse>>>any());
        assertThat(request.getValue().bucket()).isEqualTo("blog-media");
        assertThat(request.getValue().key()).isEqualTo("inline-images/a.png");
    }

    @Test
    void signsAttachmentUploadsWithAttachmentDisposition() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://account.r2.cloudflarestorage.com/blog-media/attachments/a.pdf?X-Amz-Signature=token"));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        UploadTicket ticket = storage(mock(S3Client.class), presigner)
                .createDirectUpload(new ObjectUploadRequest("attachments/a.pdf", "application/pdf", 7));

        ArgumentCaptor<PutObjectPresignRequest> request = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(request.capture());
        assertThat(request.getValue().putObjectRequest().contentDisposition()).isEqualTo("attachment");
        assertThat(ticket.requiredHeaders()).containsEntry("Content-Disposition", "attachment");
    }

    @Test
    void resolvesPublicUrlsWithAPathEncodedObjectKeyAndDeletesByLocation() throws Exception {
        S3Client client = mock(S3Client.class);
        R2ObjectStorage storage = storage(client, mock(S3Presigner.class));

        assertThat(storage.resolvePublicUrl("inline-images/space 名.png"))
                .hasToString("https://images.example.com/blog/inline-images/space%20%E5%90%8D.png");
        storage.delete("inline-images/a.png");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("blog-media");
        assertThat(request.getValue().key()).isEqualTo("inline-images/a.png");
    }

    @Test
    void rejectsNonHttpsPublicEndpointsBeforeAClientCanBeCreated() {
        R2Properties properties = new R2Properties();
        properties.setAccountId("account");
        properties.setAccessKeyId("access-key");
        properties.setSecretAccessKey("secret-key");
        properties.setBucket("blog-media");
        properties.setPublicBaseUrl("http://images.example.com");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("R2 public base URL must be an absolute HTTPS URL");
    }

    @Test
    void configuresTheR2EndpointAndAutoRegion() {
        R2Properties properties = properties();

        try (S3Client client = new R2Configuration().r2S3Client(properties)) {
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("auto");
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(java.net.URI.create("https://account.r2.cloudflarestorage.com"));
        }
    }

    private R2ObjectStorage storage(S3Client client, S3Presigner presigner) {
        return new R2ObjectStorage(client, presigner, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private R2Properties properties() {
        R2Properties properties = new R2Properties();
        properties.setAccountId("account");
        properties.setAccessKeyId("access-key");
        properties.setSecretAccessKey("secret-key");
        properties.setBucket("blog-media");
        properties.setEndpoint("https://account.r2.cloudflarestorage.com");
        properties.setPublicBaseUrl("https://images.example.com/blog");
        properties.setUploadUrlTtl(Duration.ofMinutes(10));
        return properties;
    }
}
