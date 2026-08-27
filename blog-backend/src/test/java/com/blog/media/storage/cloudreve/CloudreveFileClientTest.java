package com.blog.media.storage.cloudreve;

import com.blog.media.MediaProperties;
import com.blog.media.storage.ObjectUploadRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudreveFileClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");
    private static final int S3_PART_BYTES = 5 * 1024 * 1024;
    private static final long REPRESENTATIVE_MULTIPART_BYTES = S3_PART_BYTES + 17L;
    private final List<MockServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(MockServer::close);
    }

    @Test
    void streamsTheOfficialS3MultipartContractAndReturnsAuthoritativeMetadata() throws Exception {
        MockServer provider = server(exchange -> {
            String target = exchange.getRequestURI().getRawPath();
            if (target.equals("/multipart/part-0") || target.equals("/multipart/part-1")) {
                int part = target.endsWith("0") ? 0 : 1;
                assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
                assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/octet-stream");
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
                int expected = part == 0 ? S3_PART_BYTES : 17;
                assertThat(exchange.getRequestHeaders().getFirst("Content-Length")).isEqualTo(Integer.toString(expected));
                assertThat(countBytes(exchange.getRequestBody())).isEqualTo(expected);
                exchange.getResponseHeaders().add("ETag", "\"etag-" + (part + 1) + "\"");
                respond(exchange, 200, "");
                return;
            }
            assertThat(target).isEqualTo("/multipart/complete");
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/octet-stream");
            assertThat(read(exchange)).isEqualTo("<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>\"etag-1\"</ETag></Part>"
                    + "<Part><PartNumber>2</PartNumber><ETag>\"etag-2\"</ETag></Part></CompleteMultipartUpload>");
            respond(exchange, 200, "<CompleteMultipartUploadResult>"
                    + "<Location>https://r2.example/object</Location><Bucket>media</Bucket>"
                    + "<Key>inline-images/example.png</Key><ETag>\"final-etag\"</ETag>"
                    + "</CompleteMultipartUploadResult>");
        }, false);
        String session = fixture("s3-upload-session.json").replace("{{PROVIDER_ORIGIN}}", provider.origin().toString());
        String info = fixture("file-info.json").replace("\"size\": 5",
                "\"size\": " + REPRESENTATIVE_MULTIPART_BYTES);
        MockServer api = server(exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            if (method.equals("POST") && path.equals("/api/v4/file/create")) {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer access-example");
                JsonNode create = JSON.readTree(read(exchange));
                assertThat(create.path("type").asText()).isEqualTo("folder");
                assertThat(create.path("err_on_conflict").asBoolean()).isFalse();
                respond(exchange, 200, success(Map.of("type", 1, "id", "folder-example")));
            } else if (method.equals("PUT") && path.equals("/api/v4/file/upload")) {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer access-example");
                assertJson(read(exchange), Map.of(
                        "uri", "cloudreve://my/blog/inline-images/2026/08/example.png",
                        "size", REPRESENTATIVE_MULTIPART_BYTES,
                        "policy_id", "policy-example",
                        "last_modified", NOW.toEpochMilli(),
                        "mime_type", "image/png"));
                respond(exchange, 200, session);
            } else if (method.equals("GET") && path.equals("/api/v4/callback/s3/session-example/callback-example")) {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
                assertThat(exchange.getRequestURI().getRawQuery()).isNull();
                respond(exchange, 200, success(null));
            } else {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer access-example");
                assertThat(method).isEqualTo("GET");
                assertThat(path).isEqualTo("/api/v4/file/info");
                assertThat(query(exchange.getRequestURI())).containsExactly(
                        Map.entry("uri", "cloudreve://my/blog/inline-images/2026/08/example.png"));
                respond(exchange, 200, info);
            }
        });
        GeneratedInputStream content = new GeneratedInputStream(REPRESENTATIVE_MULTIPART_BYTES);

        CloudreveFileMetadata result = client(api, provider.origin())
                .upload("inline-images/2026/08/example.png",
                        new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png",
                                REPRESENTATIVE_MULTIPART_BYTES, REPRESENTATIVE_MULTIPART_BYTES), content);

        assertThat(result).isEqualTo(new CloudreveFileMetadata(
                "cloudreve://my/blog/inline-images/2026/08/example.png", "file-example",
                "image/png", REPRESENTATIVE_MULTIPART_BYTES, "entity-example"));
        assertThat(content.largestReadRequest()).isLessThanOrEqualTo(S3_PART_BYTES);
        assertThat(api.requests()).extracting(RecordedRequest::target).containsExactly(
                "/api/v4/file/create", "/api/v4/file/create", "/api/v4/file/create", "/api/v4/file/create",
                "/api/v4/file/upload",
                "/api/v4/callback/s3/session-example/callback-example",
                "/api/v4/file/info?uri=cloudreve%3A%2F%2Fmy%2Fblog%2Finline-images%2F2026%2F08%2Fexample.png");
        assertThat(api.requests().subList(0, 4)).extracting(RecordedRequest::body)
                .allSatisfy(body -> assertThat(body).contains("\"err_on_conflict\":false"));
        assertThat(provider.requests()).extracting(RecordedRequest::target).containsExactly(
                "/multipart/part-0", "/multipart/part-1", "/multipart/complete");
    }

    @Test
    void retainsOnlySafeCloudreveParameterErrorDetailsForDiagnostics() {
        assertThat(CloudreveFileClient.safeProviderMessage("unknown policy id"))
                .isEqualTo("unknown policy id");
        assertThat(CloudreveFileClient.safeProviderMessage("https://example.test/?token=private"))
                .isEqualTo("REDACTED");
        assertThat(CloudreveFileClient.safeProviderMessage("secret key is invalid"))
                .isEqualTo("REDACTED");
    }

    @Test
    void createsEveryDirectoryAncestorIdempotentlyIncludingAFreshConfiguredRoot() throws Exception {
        List<String> created = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger creates = new AtomicInteger();
        MockServer api = server(exchange -> {
            if (exchange.getRequestURI().getRawPath().equals("/api/v4/file/create")) {
                JsonNode body = JSON.readTree(read(exchange));
                created.add(body.path("uri").asText());
                assertThat(body.path("err_on_conflict").asBoolean()).isFalse();
                int attempt = creates.getAndIncrement();
                if (attempt == 1) {
                    respond(exchange, 200, "{\"code\":40004,\"msg\":\"already exists\"}");
                } else if (attempt == 2) {
                    respond(exchange, 409, "race");
                } else {
                    respond(exchange, 200, success(Map.of("id", "folder-" + attempt)));
                }
            } else {
                respond(exchange, 200, "{\"code\":0,\"data\":{\"session_id\":\"known-session\"},\"msg\":\"\"}");
            }
        });

        assertThatThrownBy(() -> client(api, "/blog/team", Duration.ofSeconds(2), 50L * 1024 * 1024)
                .upload("inline-images/2026/example.png",
                        new ObjectUploadRequest("inline-images/2026/example.png", "image/png", 1, 10),
                        new ByteArrayInputStream(new byte[1])))
                .isInstanceOf(CloudreveApiException.class);

        assertThat(created).containsExactly(
                "cloudreve://my/blog",
                "cloudreve://my/blog/team",
                "cloudreve://my/blog/team/inline-images",
                "cloudreve://my/blog/team/inline-images/2026");
    }

    @Test
    void sendsAndPinsTheApprovedCloudflareBackedS3Policy() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                assertThat(JSON.readTree(read(exchange)).path("policy_id").asText()).isEqualTo("policy-example");
                respond(exchange, 200, success(Map.of(
                        "session_id", "drift-session",
                        "chunk_size", 0,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "ks3"),
                        "uri", "cloudreve://my/blog/inline-images/example.png",
                        "callback_secret", "secret")));
            } else {
                aborted.set(true);
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api).upload("inline-images/example.png",
                new ObjectUploadRequest("inline-images/example.png", "image/png", 1, 10),
                new ByteArrayInputStream(new byte[1])))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
        assertThat(aborted).isTrue();
    }

    @Test
    void rejectsAMismatchedApprovedPolicyIdBeforeSendingContent() throws Exception {
        AtomicBoolean contentSent = new AtomicBoolean();
        AtomicBoolean aborted = new AtomicBoolean();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "wrong-policy-session",
                        "chunk_size", 0,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "different-policy", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/inline-images/example.png")));
            } else if (path.startsWith("/api/v4/file/upload/wrong-policy-session/")) {
                contentSent.set(true);
                respond(exchange, 200, success(null));
            } else {
                aborted.set(true);
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api).upload("inline-images/example.png",
                new ObjectUploadRequest("inline-images/example.png", "image/png", 1, 10),
                new ByteArrayInputStream(new byte[1])))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
        assertThat(contentSent).isFalse();
        assertThat(aborted).isTrue();
    }

    @Test
    void uploadsOfficialZeroChunkSizeRelaySessionsAsOneUnchunkedPart() throws Exception {
        AtomicInteger chunks = new AtomicInteger();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "relay-session",
                        "chunk_size", 0,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/inline-images/2026/08/example.png")));
            } else if (path.equals("/api/v4/file/upload/relay-session/0")) {
                chunks.incrementAndGet();
                assertThat(readBytes(exchange)).containsExactly(1, 2, 3, 4, 5);
                respond(exchange, 200, success(null));
            } else {
                respond(exchange, 200, fixture("file-info.json"));
            }
        });

        client(api).upload("inline-images/2026/08/example.png",
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5, 10),
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}));

        assertThat(chunks).hasValue(1);
    }

    @Test
    void sendsOneEmptyPartForAnOfficialZeroByteZeroChunkSizeRelaySession() throws Exception {
        AtomicInteger chunks = new AtomicInteger();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "empty-session",
                        "chunk_size", 0,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/inline-images/empty.png")));
            } else if (path.equals("/api/v4/file/upload/empty-session/0")) {
                chunks.incrementAndGet();
                assertThat(readBytes(exchange)).isEmpty();
                respond(exchange, 200, success(null));
            } else {
                respond(exchange, 200, fixture("file-info.json")
                        .replace("2026/08/example.png", "empty.png")
                        .replace("\"size\": 5", "\"size\": 0"));
            }
        });

        client(api).upload("inline-images/empty.png",
                new ObjectUploadRequest("inline-images/empty.png", "image/png", 0, 10),
                new ByteArrayInputStream(new byte[0]));

        assertThat(chunks).hasValue(1);
    }

    @Test
    void streamsAnUnchunkedRelayUploadWithoutReadingTheWholeObjectAtOnce() throws Exception {
        int byteSize = 1024 * 1024 + 17;
        AtomicInteger chunks = new AtomicInteger();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "streamed-relay-session",
                        "chunk_size", 0,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/inline-images/streamed.png")));
            } else if (path.equals("/api/v4/file/upload/streamed-relay-session/0")) {
                chunks.incrementAndGet();
                assertThat(exchange.getRequestHeaders().getFirst("Content-Length"))
                        .isEqualTo(Integer.toString(byteSize));
                assertThat(countBytes(exchange.getRequestBody())).isEqualTo(byteSize);
                respond(exchange, 200, success(null));
            } else {
                respond(exchange, 200, fixture("file-info.json")
                        .replace("2026/08/example.png", "streamed.png")
                        .replace("\"size\": 5", "\"size\": " + byteSize));
            }
        }, false);
        GeneratedInputStream content = new GeneratedInputStream(byteSize);

        client(api).upload("inline-images/streamed.png",
                new ObjectUploadRequest("inline-images/streamed.png", "image/png", byteSize, 2L * 1024 * 1024),
                content);

        assertThat(chunks).hasValue(1);
        assertThat(content.largestReadRequest()).isLessThanOrEqualTo(64 * 1024);
    }

    @Test
    void acceptsAProviderChunkLargerThanTheIndependentMaximumForASmallFinalPart() throws Exception {
        AtomicInteger chunks = new AtomicInteger();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "large-chunk-relay-session",
                        "chunk_size", 32L * 1024 * 1024,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/inline-images/small.png")));
            } else if (path.equals("/api/v4/file/upload/large-chunk-relay-session/0")) {
                chunks.incrementAndGet();
                assertThat(readBytes(exchange)).containsExactly(1);
                respond(exchange, 200, success(null));
            } else {
                respond(exchange, 200, fixture("file-info.json")
                        .replace("2026/08/example.png", "small.png")
                        .replace("\"size\": 5", "\"size\": 1"));
            }
        });

        client(api).upload("inline-images/small.png",
                new ObjectUploadRequest("inline-images/small.png", "image/png", 1, 10),
                new ByteArrayInputStream(new byte[]{1}));

        assertThat(chunks).hasValue(1);
    }

    @Test
    void rejectsDeclaredUploadsAboveTheIndependentRequestMaximumBeforeNetworkIo() throws Exception {
        MockServer api = server(exchange -> respond(exchange, 500, "must not be called"));

        assertThatThrownBy(() -> client(api).upload("inline-images/example.png",
                new ObjectUploadRequest("inline-images/example.png", "image/png", 11, 10),
                new ByteArrayInputStream(new byte[11])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
        assertThat(api.requests()).isEmpty();
    }

    @Test
    void usesOrderedZeroBasedChunkIndicesForACloudreveRelayUpload() throws Exception {
        AtomicInteger nextChunk = new AtomicInteger();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, "{\"code\":40004,\"msg\":\"already exists\"}");
            } else if (path.equals("/api/v4/file/upload")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "local-session",
                        "chunk_size", 2,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/attachments/2026/08/example.bin",
                        "callback_secret", "unused")));
            } else if (path.startsWith("/api/v4/file/upload/local-session/")) {
                int index = nextChunk.getAndIncrement();
                assertThat(path).isEqualTo("/api/v4/file/upload/local-session/" + index);
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer access-example");
                assertThat(readBytes(exchange)).hasSize(index < 2 ? 2 : 1);
                respond(exchange, 200, success(null));
            } else {
                respond(exchange, 200, fixture("file-info.json")
                        .replace("example.png", "example.bin")
                        .replace("image/png", "application/octet-stream")
                        .replace("inline-images", "attachments"));
            }
        });

        client(api).upload("attachments/2026/08/example.bin",
                new ObjectUploadRequest("attachments/2026/08/example.bin", "application/octet-stream", 5),
                new ByteArrayInputStream(new byte[5]));

        assertThat(nextChunk).hasValue(3);
        assertThat(api.requests()).extracting(RecordedRequest::method, RecordedRequest::target)
                .containsSubsequence(
                        org.assertj.core.groups.Tuple.tuple("POST", "/api/v4/file/upload/local-session/0"),
                        org.assertj.core.groups.Tuple.tuple("POST", "/api/v4/file/upload/local-session/1"),
                        org.assertj.core.groups.Tuple.tuple("POST", "/api/v4/file/upload/local-session/2"));
    }

    @Test
    void abortsTheSessionWithoutRetryingANonIdempotentFailedChunk() throws Exception {
        AtomicInteger failedChunkCalls = new AtomicInteger();
        MockServer provider = server(exchange -> {
            if (exchange.getRequestURI().getRawPath().endsWith("part-0")) {
                assertThat(countBytes(exchange.getRequestBody())).isEqualTo(S3_PART_BYTES);
                exchange.getResponseHeaders().add("ETag", "\"etag-1\"");
                respond(exchange, 200, "");
            } else {
                assertThat(countBytes(exchange.getRequestBody())).isEqualTo(17);
                failedChunkCalls.incrementAndGet();
                respond(exchange, 503, "provider unavailable");
            }
        }, false);
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (path.equals("/api/v4/file/upload") && exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, fixture("s3-upload-session.json")
                        .replace("{{PROVIDER_ORIGIN}}", provider.origin().toString()));
            } else {
                assertThat(exchange.getRequestMethod()).isEqualTo("DELETE");
                assertThat(path).isEqualTo("/api/v4/file/upload");
                assertJson(read(exchange), Map.of(
                        "id", "session-example",
                        "uri", "cloudreve://my/blog/inline-images/2026/08/example.png"));
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api, provider.origin()).upload(
                "inline-images/2026/08/example.png",
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png",
                        REPRESENTATIVE_MULTIPART_BYTES, REPRESENTATIVE_MULTIPART_BYTES),
                new GeneratedInputStream(REPRESENTATIVE_MULTIPART_BYTES)))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.TRANSIENT);

        assertThat(failedChunkCalls).hasValue(1);
        assertThat(api.requests()).anySatisfy(request -> {
            assertThat(request.method()).isEqualTo("DELETE");
            assertThat(request.target()).isEqualTo("/api/v4/file/upload");
        });
    }

    @Test
    void rejectsEmbeddedS3CompletionErrorsBeforeCallbackAndAborts() throws Exception {
        assertRejectedCompletion("<Error><Code>InvalidPart</Code><Message>sensitive</Message></Error>",
                CloudreveApiException.Kind.PROVIDER_FAILURE);
    }

    @Test
    void rejectsMalformedS3CompletionSuccessBeforeCallbackAndAborts() throws Exception {
        assertRejectedCompletion("<CompleteMultipartUploadResult><Bucket>media</Bucket>"
                        + "<Key>inline-images/example.png</Key></CompleteMultipartUploadResult>",
                CloudreveApiException.Kind.PROVIDER_FAILURE);
    }

    @Test
    void abortsARecoverableSessionIdWhenTheSessionResponseIsMalformed() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of("session_id", "recoverable-session")));
            } else {
                assertThat(exchange.getRequestMethod()).isEqualTo("DELETE");
                assertJson(read(exchange), Map.of("id", "recoverable-session",
                        "uri", "cloudreve://my/blog/inline-images/example.png"));
                aborted.set(true);
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api).upload("inline-images/example.png",
                new ObjectUploadRequest("inline-images/example.png", "image/png", 1, 10),
                new ByteArrayInputStream(new byte[1])))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
        assertThat(aborted).isTrue();
    }

    @Test
    void doesNotRetryANonIdempotentRelayChunkAfterLogicalUnauthorized() throws Exception {
        AtomicInteger chunkCalls = new AtomicInteger();
        AtomicBoolean aborted = new AtomicBoolean();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "unauthorized-session",
                        "chunk_size", 5,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/inline-images/2026/08/example.png",
                        "callback_secret", "unused")));
            } else if (exchange.getRequestMethod().equals("POST")) {
                chunkCalls.incrementAndGet();
                respond(exchange, 200, "{\"code\":401,\"msg\":\"expired\"}");
            } else {
                aborted.set(true);
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api).upload(
                "inline-images/2026/08/example.png",
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5, 10),
                new ByteArrayInputStream(new byte[5])))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.TRANSIENT);
        assertThat(chunkCalls).hasValue(1);
        assertThat(aborted).isTrue();
    }

    @Test
    void rejectsUntrustedProviderOriginsBeforeSendingAssetBytesAndAbortsTheSession() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, fixture("s3-upload-session.json")
                        .replace("{{PROVIDER_ORIGIN}}", "https://untrusted.invalid"));
            } else {
                aborted.set(true);
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api).upload(
                "inline-images/2026/08/example.png",
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png",
                        REPRESENTATIVE_MULTIPART_BYTES, REPRESENTATIVE_MULTIPART_BYTES),
                new GeneratedInputStream(REPRESENTATIVE_MULTIPART_BYTES)))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
        assertThat(aborted).isTrue();
    }

    @Test
    void rejectsAnUploadSessionForADifferentCloudreveUriBeforeSendingContent() throws Exception {
        AtomicBoolean chunkSent = new AtomicBoolean();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "wrong-uri-session",
                        "chunk_size", 5,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/outside/example.png",
                        "callback_secret", "unused")));
            } else if (path.startsWith("/api/v4/file/upload/wrong-uri-session/")) {
                chunkSent.set(true);
                respond(exchange, 200, success(null));
            } else {
                assertThat(exchange.getRequestMethod()).isEqualTo("DELETE");
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api).upload(
                "inline-images/2026/08/example.png",
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5),
                new ByteArrayInputStream(new byte[5])))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);

        assertThat(chunkSent).isFalse();
    }

    @Test
    void rejectsContentBeyondTheDeclaredSizeAndAbortsTheSession() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, success(Map.of(
                        "session_id", "bounded-session",
                        "chunk_size", 5,
                        "expires", 2_000_000_000L,
                        "storage_policy", Map.of("id", "policy-example", "type", "s3", "relay", true),
                        "uri", "cloudreve://my/blog/inline-images/2026/08/example.png",
                        "callback_secret", "unused")));
            } else if (exchange.getRequestMethod().equals("POST")) {
                assertThat(readBytes(exchange)).hasSize(5);
                respond(exchange, 200, success(null));
            } else {
                aborted.set(true);
                respond(exchange, 200, success(null));
            }
        });

        assertThatThrownBy(() -> client(api).upload(
                "inline-images/2026/08/example.png",
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5),
                new ByteArrayInputStream(new byte[6])))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
        assertThat(aborted).isTrue();
    }

    @Test
    void retriesAnAuthenticatedApiCallOnlyOnceAfterUnauthorized() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        MockServer api = server(exchange -> {
            int attempt = attempts.getAndIncrement();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo(attempt == 0 ? "Bearer stale-access" : "Bearer refreshed-access");
            respond(exchange, 200, attempt == 0 ? "{\"code\":401,\"msg\":\"expired\"}" : fixture("file-info.json"));
        });
        CloudreveTokenService tokens = Mockito.mock(CloudreveTokenService.class);
        when(tokens.validAccessToken()).thenReturn("stale-access");
        when(tokens.validAccessTokenAfterRejection("stale-access")).thenReturn("refreshed-access");

        CloudreveFileMetadata metadata = client(api, tokens).inspect("inline-images/2026/08/example.png");

        assertThat(metadata.byteSize()).isEqualTo(5);
        assertThat(attempts).hasValue(2);
        verify(tokens, times(1)).validAccessToken();
        verify(tokens, times(1)).validAccessTokenAfterRejection("stale-access");
    }

    @Test
    void retriesAnAuthenticatedApiCallOnlyOnceAfterHttp401() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        MockServer api = server(exchange -> {
            int attempt = attempts.getAndIncrement();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo(attempt == 0 ? "Bearer stale-access" : "Bearer refreshed-access");
            respond(exchange, attempt == 0 ? 401 : 200,
                    attempt == 0 ? "unauthorized" : fixture("file-info.json"));
        });
        CloudreveTokenService tokens = Mockito.mock(CloudreveTokenService.class);
        when(tokens.validAccessToken()).thenReturn("stale-access");
        when(tokens.validAccessTokenAfterRejection("stale-access")).thenReturn("refreshed-access");

        assertThat(client(api, tokens).inspect("inline-images/2026/08/example.png").byteSize()).isEqualTo(5);
        assertThat(attempts).hasValue(2);
        verify(tokens, times(1)).validAccessTokenAfterRejection("stale-access");
    }

    @Test
    void doesNotRetryUnauthorizedMoreThanOnce() throws Exception {
        MockServer api = server(exchange -> respond(exchange, 200, "{\"code\":401,\"msg\":\"expired\"}"));
        CloudreveTokenService tokens = Mockito.mock(CloudreveTokenService.class);
        when(tokens.validAccessToken()).thenReturn("first-access");
        when(tokens.validAccessTokenAfterRejection("first-access")).thenReturn("second-access");

        assertThatThrownBy(() -> client(api, tokens).inspect("inline-images/2026/08/example.png"))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.TRANSIENT);

        assertThat(api.requests()).hasSize(2);
        verify(tokens, times(1)).validAccessToken();
        verify(tokens, times(1)).validAccessTokenAfterRejection("first-access");
    }

    @Test
    void mapsUnavailableStoredAuthorizationToATransientClientFailure() throws Exception {
        MockServer api = server(exchange -> respond(exchange, 500, "request should not be sent"));
        CloudreveTokenService tokens = Mockito.mock(CloudreveTokenService.class);
        when(tokens.validAccessToken()).thenThrow(new CloudreveAuthorizationRequiredException());

        assertThatThrownBy(() -> client(api, tokens).inspect("inline-images/2026/08/example.png"))
                .isInstanceOf(CloudreveApiException.class)
                .hasMessageNotContaining("access")
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.TRANSIENT);
        assertThat(api.requests()).isEmpty();
    }

    @Test
    void mapsCloudreveAndHttpFailuresToProviderNeutralCategories() throws Exception {
        assertFailure("{\"code\":404,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.NOT_FOUND);
        assertFailure("{\"code\":40044,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.NOT_FOUND);
        assertFailure("{\"code\":409,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.CONFLICT);
        assertFailure("{\"code\":40004,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.CONFLICT);
        assertFailure("{\"code\":50001,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.TRANSIENT);
        assertFailure("{\"code\":50004,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.TRANSIENT);
        assertFailure("{\"code\":50006,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.TRANSIENT);
        assertFailure("{\"code\":50007,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.TRANSIENT);
        assertFailure("{\"code\":0,\"data\":{}}", 429, CloudreveApiException.Kind.TRANSIENT);
        assertFailure("provider unavailable", 503, CloudreveApiException.Kind.TRANSIENT);
        assertFailure("{malformed", 200, CloudreveApiException.Kind.PROVIDER_FAILURE);
    }

    @Test
    void boundsJsonResponseBodiesAndTheCompleteRequestDeadline() throws Exception {
        assertFailure("x".repeat(64 * 1024 + 1), 200, CloudreveApiException.Kind.PROVIDER_FAILURE);

        CountDownLatch release = new CountDownLatch(1);
        MockServer stalled = server(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 20);
            exchange.getResponseBody().write("{\"code\":0".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        long started = System.nanoTime();
        try {
            assertThatThrownBy(() -> client(stalled, Duration.ofMillis(60)).inspect("inline-images/2026/08/example.png"))
                    .isInstanceOf(CloudreveApiException.class)
                    .extracting(error -> ((CloudreveApiException) error).kind())
                    .isEqualTo(CloudreveApiException.Kind.TRANSIENT);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        } finally {
            release.countDown();
        }
    }

    @Test
    void followsOnlyAllowlistedDownloadRedirectsAndClosingTheReturnedStreamClosesTheResponseBody() throws Exception {
        CountDownLatch bodyClosed = new CountDownLatch(1);
        MockServer provider = server(exchange -> {
            if (exchange.getRequestURI().getRawPath().equals("/download/start")) {
                exchange.getResponseHeaders().add("Location", providerOrigin(exchange) + "/download/content");
                respond(exchange, 302, "");
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            try {
                while (true) {
                    exchange.getResponseBody().write(new byte[]{7});
                    exchange.getResponseBody().flush();
                }
            } catch (IOException disconnected) {
            } finally {
                bodyClosed.countDown();
                exchange.close();
            }
        });
        MockServer api = server(exchange -> respond(exchange, 200, success(Map.of(
                "urls", List.of(Map.of("url", provider.origin() + "/download/start")),
                "expires", "2030-01-01T00:00:00Z"))));

        try (InputStream stream = client(api, provider.origin()).open("inline-images/2026/08/example.png")) {
            assertThat(stream.read()).isEqualTo(7);
        }

        assertThat(bodyClosed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(provider.requests()).extracting(RecordedRequest::target)
                .containsExactly("/download/start", "/download/content");
        assertJson(api.requests().getFirst().body(), Map.of(
                "uris", List.of("cloudreve://my/blog/inline-images/2026/08/example.png"),
                "download", false,
                "redirect", false,
                "archive", false,
                "no_cache", true));
    }

    @Test
    void boundsDownloadedBytesWithoutBufferingTheWholeResponseAndClosesTheSubscription() throws Exception {
        CountDownLatch bodyClosed = new CountDownLatch(1);
        MockServer provider = server(exchange -> {
            exchange.sendResponseHeaders(200, 6);
            try {
                exchange.getResponseBody().write(new byte[]{1, 2, 3, 4, 5, 6});
                exchange.getResponseBody().flush();
            } finally {
                bodyClosed.countDown();
                exchange.close();
            }
        });
        MockServer api = downloadUrlServer(provider.origin() + "/oversize");

        try (InputStream stream = client(api, "/blog", Duration.ofSeconds(2), 4, provider.origin())
                .open("inline-images/2026/08/example.png")) {
            assertThatThrownBy(stream::readAllBytes)
                    .isInstanceOf(CloudreveApiException.class)
                    .extracting(error -> ((CloudreveApiException) error).kind())
                    .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
        }

        assertThat(bodyClosed.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancelsAStalledDownloadAtTheTotalPostHeaderDeadline() throws Exception {
        CountDownLatch bodyClosed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MockServer provider = server(exchange -> {
            exchange.sendResponseHeaders(200, 10);
            try {
                exchange.getResponseBody().write(7);
                exchange.getResponseBody().flush();
                release.await(2, TimeUnit.SECONDS);
                exchange.getResponseBody().write(8);
            } catch (IOException disconnected) {
                // Cancellation may be observed here or only when the handler exits.
            } finally {
                bodyClosed.countDown();
                exchange.close();
            }
        });
        MockServer api = downloadUrlServer(provider.origin() + "/stall");
        long started = System.nanoTime();
        try (InputStream stream = client(api, "/blog", Duration.ofMillis(80), 1024, provider.origin())
                .open("inline-images/2026/08/example.png")) {
            assertThat(stream.read()).isEqualTo(7);
            assertThatThrownBy(stream::read)
                    .isInstanceOf(CloudreveApiException.class)
                    .extracting(error -> ((CloudreveApiException) error).kind())
                    .isEqualTo(CloudreveApiException.Kind.TRANSIENT);
        } finally {
            release.countDown();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(bodyClosed.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void enforcesATotalDeadlineEvenWhenTheDownloadKeepsDrippingBytes() throws Exception {
        CountDownLatch bodyClosed = new CountDownLatch(1);
        MockServer provider = server(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                for (int index = 0; index < 100; index++) {
                    exchange.getResponseBody().write(index);
                    exchange.getResponseBody().flush();
                    TimeUnit.MILLISECONDS.sleep(20);
                }
            } catch (IOException disconnected) {
                bodyClosed.countDown();
            } finally {
                exchange.close();
            }
        });
        MockServer api = downloadUrlServer(provider.origin() + "/drip");

        long started = System.nanoTime();
        try (InputStream stream = client(api, "/blog", Duration.ofMillis(100), 1024, provider.origin())
                .open("inline-images/2026/08/example.png")) {
            assertThatThrownBy(stream::readAllBytes)
                    .isInstanceOf(CloudreveApiException.class)
                    .extracting(error -> ((CloudreveApiException) error).kind())
                    .isEqualTo(CloudreveApiException.Kind.TRANSIENT);
        }

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(bodyClosed.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void rejectsADownloadRedirectOutsideTheAllowlist() throws Exception {
        MockServer provider = server(exchange -> {
            exchange.getResponseHeaders().add("Location", "https://untrusted.invalid/content");
            respond(exchange, 302, "");
        });
        MockServer api = server(exchange -> respond(exchange, 200, success(Map.of(
                "urls", List.of(Map.of("url", provider.origin() + "/download/start")),
                "expires", "2030-01-01T00:00:00Z"))));

        assertThatThrownBy(() -> client(api, provider.origin()).open("inline-images/2026/08/example.png"))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
    }

    @Test
    void sendsTheOfficialInspectAndPermanentDeleteContracts() throws Exception {
        MockServer api = server(exchange -> {
            if (exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 200, fixture("file-info.json"));
            } else {
                assertThat(exchange.getRequestMethod()).isEqualTo("DELETE");
                assertJson(read(exchange), Map.of(
                        "uris", List.of("cloudreve://my/blog/inline-images/2026/08/example.png"),
                        "unlink", false,
                        "skip_soft_delete", true));
                respond(exchange, 200, success(null));
            }
        });

        CloudreveFileClient client = client(api);
        assertThat(client.inspect("inline-images/2026/08/example.png").contentType()).isEqualTo("image/png");
        client.delete("inline-images/2026/08/example.png");

        assertThat(api.requests()).extracting(RecordedRequest::method, RecordedRequest::target)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("GET", "/api/v4/file/info?uri=cloudreve%3A%2F%2Fmy%2Fblog%2Finline-images%2F2026%2F08%2Fexample.png"),
                        org.assertj.core.groups.Tuple.tuple("DELETE", "/api/v4/file"));
    }

    @Test
    void derivesTheStoredContentTypeFromTheConstrainedObjectExtensionWithoutPrivateMetadata() throws Exception {
        MockServer api = server(exchange -> respond(exchange, 200, success(Map.of(
                "type", 0,
                "id", "file-example",
                "path", "cloudreve://my/blog/inline-images/2026/08/example.png",
                "size", 3,
                "metadata", Map.of(),
                "primary_entity", "entity-example"))));

        assertThat(client(api).inspect("inline-images/2026/08/example.png").contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsTraversalMismatchedKeysAndMalformedSuccessfulMetadataWithoutLeakingProviderBodies() throws Exception {
        MockServer api = server(exchange -> respond(exchange, 200, "{\"code\":0,\"data\":{\"id\":\"file\"},\"msg\":\"sensitive-provider-body\"}"));
        CloudreveFileClient client = client(api);

        assertThatThrownBy(() -> client.inspect("../outside.png")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.upload("inline-images/a.png",
                new ObjectUploadRequest("inline-images/b.png", "image/png", 1), new ByteArrayInputStream(new byte[1])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.inspect("inline-images/2026/08/example.png"))
                .isInstanceOf(CloudreveApiException.class)
                .hasMessageNotContaining("sensitive-provider-body")
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(CloudreveApiException.Kind.PROVIDER_FAILURE);
    }

    @Test
    void doesNotCreateTheNetworkClientForADisabledUnconfiguredInstallation() {
        new ApplicationContextRunner()
                .withUserConfiguration(CloudreveConfiguration.class, CloudreveFileClient.class)
                .withBean(CloudreveTokenService.class, () -> Mockito.mock(CloudreveTokenService.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CloudreveFileClient.class);
                });
    }

    private void assertFailure(String body, int status, CloudreveApiException.Kind expected) throws Exception {
        MockServer api = server(exchange -> respond(exchange, status, body));
        assertThatThrownBy(() -> client(api).inspect("inline-images/2026/08/example.png"))
                .isInstanceOf(CloudreveApiException.class)
                .hasMessageNotContaining("private detail")
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(expected);
    }

    private MockServer downloadUrlServer(String url) throws Exception {
        return server(exchange -> respond(exchange, 200, success(Map.of(
                "urls", List.of(Map.of("url", url)),
                "expires", "2030-01-01T00:00:00Z"))));
    }

    private void assertRejectedCompletion(String completionXml, CloudreveApiException.Kind expected) throws Exception {
        AtomicBoolean callback = new AtomicBoolean();
        AtomicBoolean aborted = new AtomicBoolean();
        MockServer provider = server(exchange -> {
            if (exchange.getRequestURI().getRawPath().equals("/part")) {
                exchange.getResponseHeaders().add("ETag", "\"part-etag\"");
                respond(exchange, 200, "");
            } else {
                respond(exchange, 200, completionXml);
            }
        });
        MockServer api = server(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api/v4/file/create")) {
                respond(exchange, 200, success(Map.of("id", "folder")));
            } else if (exchange.getRequestMethod().equals("PUT")) {
                respond(exchange, 200, s3Session(provider.origin(), "completion-session", 1,
                        List.of(provider.origin() + "/part"), provider.origin() + "/complete",
                        "cloudreve://my/blog/inline-images/example.png"));
            } else if (path.startsWith("/api/v4/callback/")) {
                callback.set(true);
                respond(exchange, 200, success(null));
            } else if (exchange.getRequestMethod().equals("DELETE")) {
                aborted.set(true);
                respond(exchange, 200, success(null));
            } else {
                respond(exchange, 200, fixture("file-info.json"));
            }
        });

        assertThatThrownBy(() -> client(api, provider.origin()).upload("inline-images/example.png",
                new ObjectUploadRequest("inline-images/example.png", "image/png", 1, 10),
                new ByteArrayInputStream(new byte[]{1})))
                .isInstanceOf(CloudreveApiException.class)
                .extracting(error -> ((CloudreveApiException) error).kind())
                .isEqualTo(expected);
        assertThat(callback).isFalse();
        assertThat(aborted).isTrue();
    }

    private static String s3Session(URI provider, String id, long chunkSize, List<String> uploadUrls,
                                    String completeUrl, String uri) throws Exception {
        return success(Map.of(
                "session_id", id,
                "chunk_size", chunkSize,
                "expires", 2_000_000_000L,
                "upload_urls", uploadUrls,
                "completeURL", completeUrl,
                "storage_policy", Map.of("id", "policy-example", "type", "s3"),
                "uri", uri,
                "callback_secret", "callback-secret"));
    }

    private CloudreveFileClient client(MockServer api, URI... providerOrigins) {
        return client(api, tokens(), Duration.ofSeconds(2), providerOrigins);
    }

    private CloudreveFileClient client(MockServer api, Duration timeout, URI... providerOrigins) {
        return client(api, tokens(), timeout, providerOrigins);
    }

    private CloudreveFileClient client(MockServer api, CloudreveTokenService tokens, URI... providerOrigins) {
        return client(api, tokens, Duration.ofSeconds(2), providerOrigins);
    }

    private CloudreveFileClient client(MockServer api, CloudreveTokenService tokens, Duration timeout, URI... providerOrigins) {
        return client(api, tokens, "/blog", timeout, 50L * 1024 * 1024, providerOrigins);
    }

    private CloudreveFileClient client(MockServer api, String rootPath, Duration timeout, long maximumBytes,
                                       URI... providerOrigins) {
        return client(api, tokens(), rootPath, timeout, maximumBytes, providerOrigins);
    }

    private CloudreveFileClient client(MockServer api, CloudreveTokenService tokens, String rootPath,
                                       Duration timeout, long maximumBytes, URI... providerOrigins) {
        CloudreveProperties properties = new CloudreveProperties();
        properties.setBaseUrl(api.origin());
        properties.setRootPath(rootPath);
        properties.setPolicyId("policy-example");
        properties.setRequestTimeout(timeout);
        properties.setConnectTimeout(timeout);
        properties.setAllowTrustedInternalHttp(true);
        properties.setProviderOrigins(List.of(providerOrigins));
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        MediaProperties media = new MediaProperties();
        media.setMaxBytes(maximumBytes);
        media.setMaxAttachmentBytes(maximumBytes);
        media.setMaxZipAttachmentBytes(maximumBytes);
        return new CloudreveFileClient(properties, media, tokens, http, JSON, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CloudreveTokenService tokens() {
        CloudreveTokenService tokens = Mockito.mock(CloudreveTokenService.class);
        when(tokens.validAccessToken()).thenReturn("access-example");
        return tokens;
    }

    private MockServer server(Responder responder) throws IOException {
        return server(responder, true);
    }

    private MockServer server(Responder responder, boolean captureRequestBody) throws IOException {
        MockServer server = new MockServer(responder, captureRequestBody);
        servers.add(server);
        return server;
    }

    private static String fixture(String name) throws IOException {
        try (InputStream stream = CloudreveFileClientTest.class.getResourceAsStream("/cloudreve/v4/" + name)) {
            if (stream == null) throw new IOException("Missing fixture " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String success(Object data) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 0);
        if (data != null) envelope.put("data", data);
        envelope.put("msg", "");
        return JSON.writeValueAsString(envelope);
    }

    private static void assertJson(String actual, Object expected) throws IOException {
        assertThat(JSON.readTree(actual)).isEqualTo(JSON.readTree(JSON.writeValueAsBytes(expected)));
    }

    private static Map<String, String> query(URI uri) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (uri.getRawQuery() == null) return result;
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), decode(parts.length == 1 ? "" : parts[1]));
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String providerOrigin(HttpExchange exchange) {
        return "http://localhost:" + exchange.getLocalAddress().getPort();
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(readBytes(exchange), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(HttpExchange exchange) throws IOException {
        return exchange.getRequestBody().readAllBytes();
    }

    private static long countBytes(InputStream stream) throws IOException {
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = stream.read(buffer)) >= 0) count += read;
        return count;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (!body.isEmpty()) exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private interface Responder {
        void respond(HttpExchange exchange) throws Exception;
    }

    private record RecordedRequest(String method, String target, String authorization, String body) {
    }

    private static final class MockServer implements AutoCloseable {
        private final HttpServer server;
        private final List<RecordedRequest> requests = java.util.Collections.synchronizedList(new ArrayList<>());

        private MockServer(Responder responder, boolean captureRequestBody) throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = captureRequestBody ? exchange.getRequestBody().readAllBytes() : new byte[0];
                requests.add(new RecordedRequest(exchange.getRequestMethod(),
                        exchange.getRequestURI().toString(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        new String(body, StandardCharsets.UTF_8)));
                if (captureRequestBody) exchange.setStreams(new ByteArrayInputStream(body), exchange.getResponseBody());
                try {
                    responder.respond(exchange);
                } catch (Throwable failure) {
                    try {
                        respond(exchange, 500, "test fixture failure");
                    } catch (IOException ignored) {
                    }
                    if (failure instanceof AssertionError assertion) throw assertion;
                    throw new RuntimeException(failure);
                }
            });
            server.start();
        }

        URI origin() {
            return URI.create("http://localhost:" + server.getAddress().getPort());
        }

        List<RecordedRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private int largestReadRequest;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            largestReadRequest = Math.max(largestReadRequest, length);
            return super.read(bytes, offset, length);
        }

        int largestReadRequest() {
            return largestReadRequest;
        }
    }

    private static final class GeneratedInputStream extends InputStream {
        private final long size;
        private long position;
        private int largestReadRequest;

        private GeneratedInputStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (position >= size) return -1;
            return (int) (position++ % 251);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (position >= size) return -1;
            int count = (int) Math.min(length, size - position);
            largestReadRequest = Math.max(largestReadRequest, length);
            for (int index = 0; index < count; index++) bytes[offset + index] = (byte) ((position + index) % 251);
            position += count;
            return count;
        }

        int largestReadRequest() {
            return largestReadRequest;
        }
    }
}
