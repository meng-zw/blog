package com.blog.media.storage.cloudreve;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudreveFileClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");
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
                byte[] expected = part == 0 ? new byte[]{1, 2, 3} : new byte[]{4, 5};
                assertThat(exchange.getRequestHeaders().getFirst("Content-Length")).isEqualTo(Integer.toString(expected.length));
                assertThat(exchange.getRequestBody().readAllBytes()).containsExactly(expected);
                exchange.getResponseHeaders().add("ETag", "\"etag-" + (part + 1) + "\"");
                respond(exchange, 200, "");
                return;
            }
            assertThat(target).isEqualTo("/multipart/complete");
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/octet-stream");
            assertThat(read(exchange)).isEqualTo("<CompleteMultipartUpload><Part><PartNumber>1</PartNumber><ETag>\"etag-1\"</ETag></Part>"
                    + "<Part><PartNumber>2</PartNumber><ETag>\"etag-2\"</ETag></Part></CompleteMultipartUpload>");
            respond(exchange, 200, "<CompleteMultipartUploadResult/>");
        });
        String session = fixture("s3-upload-session.json").replace("{{PROVIDER_ORIGIN}}", provider.origin().toString());
        String info = fixture("file-info.json");
        MockServer api = server(exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            if (method.equals("POST") && path.equals("/api/v4/file/create")) {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer access-example");
                assertJson(read(exchange), Map.of(
                        "type", "folder",
                        "uri", "cloudreve://my/blog/inline-images/2026/08",
                        "err_on_conflict", false));
                respond(exchange, 200, success(Map.of("type", 1, "id", "folder-example")));
            } else if (method.equals("PUT") && path.equals("/api/v4/file/upload")) {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer access-example");
                assertJson(read(exchange), Map.of(
                        "uri", "cloudreve://my/blog/inline-images/2026/08/example.png",
                        "size", 5,
                        "mime_type", "image/png",
                        "metadata", Map.of("blog:mime_type", "image/png")));
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
        TrackingInputStream content = new TrackingInputStream(new byte[]{1, 2, 3, 4, 5});

        CloudreveFileMetadata result = client(api, provider.origin())
                .upload("inline-images/2026/08/example.png",
                        new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5), content);

        assertThat(result).isEqualTo(new CloudreveFileMetadata(
                "cloudreve://my/blog/inline-images/2026/08/example.png", "file-example",
                "image/png", 5, "entity-example"));
        assertThat(content.largestReadRequest()).isLessThanOrEqualTo(3);
        assertThat(api.requests()).extracting(RecordedRequest::target).containsExactly(
                "/api/v4/file/create", "/api/v4/file/upload",
                "/api/v4/callback/s3/session-example/callback-example",
                "/api/v4/file/info?uri=cloudreve%3A%2F%2Fmy%2Fblog%2Finline-images%2F2026%2F08%2Fexample.png");
        assertThat(provider.requests()).extracting(RecordedRequest::target).containsExactly(
                "/multipart/part-0", "/multipart/part-1", "/multipart/complete");
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
                        "storage_policy", Map.of("id", "local-policy", "type", "local", "relay", true),
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
                exchange.getResponseHeaders().add("ETag", "\"etag-1\"");
                respond(exchange, 200, "");
            } else {
                failedChunkCalls.incrementAndGet();
                respond(exchange, 503, "provider unavailable");
            }
        });
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
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5),
                new ByteArrayInputStream(new byte[5])))
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
                        "storage_policy", Map.of("id", "local-policy", "type", "local"),
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
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5),
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
                new ObjectUploadRequest("inline-images/2026/08/example.png", "image/png", 5),
                new ByteArrayInputStream(new byte[5])))
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
                        "storage_policy", Map.of("id", "local-policy", "type", "local"),
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
                        "storage_policy", Map.of("id", "local-policy", "type", "local"),
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
        assertFailure("{\"code\":409,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.CONFLICT);
        assertFailure("{\"code\":40004,\"msg\":\"private detail\"}", 200, CloudreveApiException.Kind.CONFLICT);
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
                bodyClosed.countDown();
            } finally {
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
        CloudreveProperties properties = new CloudreveProperties();
        properties.setBaseUrl(api.origin());
        properties.setRootPath("/blog");
        properties.setRequestTimeout(timeout);
        properties.setConnectTimeout(timeout);
        properties.setAllowTrustedInternalHttp(true);
        properties.setProviderOrigins(List.of(providerOrigins));
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new CloudreveFileClient(properties, tokens, http, JSON, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CloudreveTokenService tokens() {
        CloudreveTokenService tokens = Mockito.mock(CloudreveTokenService.class);
        when(tokens.validAccessToken()).thenReturn("access-example");
        return tokens;
    }

    private MockServer server(Responder responder) throws IOException {
        MockServer server = new MockServer(responder);
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
        assertThat(JSON.readTree(actual)).isEqualTo(JSON.valueToTree(expected));
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

        private MockServer(Responder responder) throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                requests.add(new RecordedRequest(exchange.getRequestMethod(),
                        exchange.getRequestURI().toString(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        new String(body, StandardCharsets.UTF_8)));
                exchange.setStreams(new ByteArrayInputStream(body), exchange.getResponseBody());
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
}
