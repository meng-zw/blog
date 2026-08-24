package com.blog.media.storage.cloudreve;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudreveOAuthClientTest {
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void buildsS256AuthorizationUrlWithoutBase64Padding() {
        CloudreveOAuthClient client = client(new StubHttpClient());

        URI uri = client.authorizationUri("state-value", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
        Map<String, String> query = query(uri);

        assertThat(query).containsEntry("response_type", "code")
                .containsEntry("client_id", "client-id")
                .containsEntry("redirect_uri", "https://blog.example/oauth/callback")
                .containsEntry("scope", "openid profile offline_access Files.Write")
                .containsEntry("state", "state-value")
                .containsEntry("code_challenge_method", "S256");
        assertThat(query.get("code_challenge"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                .doesNotContain("=");
    }

    @Test
    void exchangesAuthorizationCodeAsFormAndTreatsReturnedScopeAsAuthoritative() {
        StubHttpClient http = new StubHttpClient();
        http.enqueue(200, """
                {"access_token":"access","refresh_token":"refresh","token_type":"Bearer",
                 "expires_in":3600,"refresh_token_expires_in":7200,
                 "scope":"openid profile offline_access Files.Write"}
                """);

        CloudreveOAuthClient.TokenPair pair = client(http).exchangeCode("code a&b", "verifier");

        HttpRequest request = http.requests.getFirst();
        assertThat(request.uri()).isEqualTo(URI.create("https://cloud.example/api/v4/session/oauth/token"));
        assertThat(request.headers().firstValue("Content-Type")).hasValue("application/x-www-form-urlencoded");
        assertThat(http.bodies.getFirst()).contains("grant_type=authorization_code")
                .contains("client_id=client-id")
                .contains("client_secret=client-secret")
                .contains("code=code+a%26b")
                .contains("code_verifier=verifier");
        assertThat(pair.accessToken()).isEqualTo("access");
        assertThat(pair.refreshToken()).isEqualTo("refresh");
        assertThat(pair.accessExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(pair.refreshExpiresAt()).isEqualTo(NOW.plusSeconds(7200));
        assertThat(pair.scopes()).containsExactlyInAnyOrder("openid", "profile", "offline_access", "Files.Write");
    }

    @Test
    void rejectsExchangeWhenReturnedScopeOmitsWriteOrOfflineAccess() {
        StubHttpClient http = new StubHttpClient();
        http.enqueue(200, """
                {"access_token":"access","refresh_token":"refresh","token_type":"Bearer",
                 "expires_in":3600,"refresh_token_expires_in":7200,"scope":"openid profile"}
                """);

        assertThatThrownBy(() -> client(http).exchangeCode("code", "verifier"))
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class)
                .hasMessageNotContaining("access")
                .hasMessageNotContaining("refresh");
    }

    @Test
    void rejectsANonBearerTokenResponse() {
        StubHttpClient http = new StubHttpClient();
        http.enqueue(200, """
                {"access_token":"access","refresh_token":"refresh","token_type":"MAC",
                 "expires_in":3600,"refresh_token_expires_in":7200,
                 "scope":"openid profile offline_access Files.Write"}
                """);

        assertThatThrownBy(() -> client(http).exchangeCode("code", "verifier"))
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class)
                .hasMessageNotContaining("access")
                .hasMessageNotContaining("refresh");
    }

    @Test
    void refreshesWithJsonAndUsesRotatedRefreshToken() {
        StubHttpClient http = new StubHttpClient();
        http.enqueue(200, """
                {"code":0,"data":{"access_token":"new-access","refresh_token":"new-refresh",
                 "access_expires":"2026-08-24T01:00:00Z","refresh_expires":"2026-09-24T00:00:00Z"},"msg":""}
                """);

        CloudreveOAuthClient.TokenPair pair = client(http).refresh("old-refresh", List.of("offline_access", "Files.Write"));

        assertThat(http.requests.getFirst().headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(http.bodies.getFirst()).isEqualTo("{\"refresh_token\":\"old-refresh\"}");
        assertThat(pair.accessToken()).isEqualTo("new-access");
        assertThat(pair.refreshToken()).isEqualTo("new-refresh");
        assertThat(pair.scopes()).containsExactly("offline_access", "Files.Write");
    }

    @Test
    void distinguishesInvalidGrantFromTransientFailuresWithoutLeakingBodies() {
        StubHttpClient invalid = new StubHttpClient();
        invalid.enqueue(400, "{\"error\":\"invalid_grant\",\"error_description\":\"sensitive-token\"}");
        StubHttpClient cloudreveInvalid = new StubHttpClient();
        cloudreveInvalid.enqueue(200, "{\"code\":40020,\"msg\":\"sensitive-credential-detail\"}");
        StubHttpClient unavailable = new StubHttpClient();
        unavailable.enqueue(503, "sensitive-provider-body");

        assertThatThrownBy(() -> client(invalid).refresh("refresh", List.of("offline_access", "Files.Write")))
                .isInstanceOf(CloudreveOAuthClient.InvalidGrantException.class)
                .hasMessageNotContaining("sensitive-token");
        assertThatThrownBy(() -> client(cloudreveInvalid).refresh("refresh", List.of("offline_access", "Files.Write")))
                .isInstanceOf(CloudreveOAuthClient.InvalidGrantException.class)
                .hasMessageNotContaining("sensitive-credential-detail");
        assertThatThrownBy(() -> client(unavailable).refresh("refresh", List.of("offline_access", "Files.Write")))
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class)
                .hasMessageNotContaining("sensitive-provider-body");
    }

    @Test
    void fetchesOpenIdUserInfoWithBearerToken() {
        StubHttpClient http = new StubHttpClient();
        http.enqueue(200, "{\"sub\":\"subject-1\",\"name\":\"Cloud User\",\"preferred_username\":\"cloud-user\"}");

        CloudreveOAuthClient.UserInfo info = client(http).userInfo("access-token");

        assertThat(http.requests.getFirst().headers().firstValue("Authorization")).hasValue("Bearer access-token");
        assertThat(info.subject()).isEqualTo("subject-1");
        assertThat(info.displayName()).isEqualTo("Cloud User");
    }

    @Test
    void timeoutIsRetryableAndDoesNotExposeTheToken() {
        StubHttpClient http = new StubHttpClient();
        http.fail(new HttpTimeoutException("access-token timeout detail"));

        assertThatThrownBy(() -> client(http).userInfo("access-token"))
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class)
                .hasMessageNotContaining("access-token");
    }

    @Test
    void rejectsAnOversizedResponseBeforeParsingIt() {
        StubHttpClient http = new StubHttpClient();
        http.enqueue(200, "x".repeat(64 * 1024 + 1));

        assertThatThrownBy(() -> client(http).exchangeCode("code", "verifier"))
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class);
    }

    @Test
    void emptyAndMalformedJsonResponsesAreTypedRetryableProtocolFailures() {
        StubHttpClient empty = new StubHttpClient();
        empty.enqueue(200, "");
        StubHttpClient malformed = new StubHttpClient();
        malformed.enqueue(200, "{not-json");

        assertThatThrownBy(() -> client(empty).refresh("refresh", List.of("offline_access", "Files.Write")))
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class);
        assertThatThrownBy(() -> client(malformed).refresh("refresh", List.of("offline_access", "Files.Write")))
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class);
    }

    @Test
    void uncheckedJsonDecoderFailureIsNormalizedWithoutLeakingTheBody() throws Exception {
        StubHttpClient http = new StubHttpClient();
        http.enqueue(200, "sensitive-provider-body");
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenReturn("{\"refresh_token\":\"refresh\"}");
        when(failingMapper.readTree(any(byte[].class))).thenThrow(new IllegalStateException("decoder failed"));
        CloudreveProperties properties = properties(Duration.ofSeconds(1));
        CloudreveOAuthClient client = new CloudreveOAuthClient(
                properties, http, failingMapper, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> client.refresh("refresh", List.of("offline_access", "Files.Write")))
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class)
                .hasMessageNotContaining("sensitive-provider-body");
    }

    @Test
    void totalDeadlineCancelsAResponseThatSendsHeadersThenStalls() throws Exception {
        StreamingHttpClient http = new StreamingHttpClient(false);
        long started = System.nanoTime();

        assertThatThrownBy(() -> client(http, Duration.ofMillis(50)).userInfo("access-token"))
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(http.cancelled.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void dripFeedingAResponseCannotKeepExtendingTheTotalDeadline() throws Exception {
        StreamingHttpClient http = new StreamingHttpClient(true);
        long started = System.nanoTime();

        assertThatThrownBy(() -> client(http, Duration.ofMillis(60)).userInfo("access-token"))
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(http.cancelled.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void tokenPairToStringRedactsBothCredentials() {
        CloudreveOAuthClient.TokenPair pair = new CloudreveOAuthClient.TokenPair(
                "access-secret", "refresh-secret", NOW.plusSeconds(60), NOW.plusSeconds(120),
                List.of("offline_access", "Files.Write"));

        assertThat(pair.toString()).doesNotContain("access-secret").doesNotContain("refresh-secret");
    }

    private static CloudreveOAuthClient client(HttpClient httpClient) {
        return client(httpClient, Duration.ofSeconds(3));
    }

    private static CloudreveOAuthClient client(HttpClient httpClient, Duration requestTimeout) {
        CloudreveProperties properties = properties(requestTimeout);
        return new CloudreveOAuthClient(properties, httpClient, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CloudreveProperties properties(Duration requestTimeout) {
        CloudreveProperties properties = new CloudreveProperties();
        properties.setBaseUrl(URI.create("https://cloud.example"));
        properties.setRedirectUri(URI.create("https://blog.example/oauth/callback"));
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRequestTimeout(requestTimeout);
        return properties;
    }

    private static Map<String, String> query(URI uri) {
        return java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        pair -> decode(pair[0]), pair -> decode(pair.length == 1 ? "" : pair[1])));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static final class StubHttpClient extends HttpClient {
        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();
        private final java.util.ArrayDeque<StubResponse> responses = new java.util.ArrayDeque<>();
        private IOException failure;

        void enqueue(int status, String body) { responses.add(new StubResponse(status, body)); }
        void fail(IOException failure) { this.failure = failure; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            if (failure != null) throw failure;
            requests.add(request);
            bodies.add(readBody(request));
            StubResponse next = responses.remove();
            return (HttpResponse<T>) next.response(request);
        }

        private static String readBody(HttpRequest request) throws IOException {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElse(null);
            if (publisher == null) return "";
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            publisher.subscribe(new Flow.Subscriber<>() {
                public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(ByteBuffer item) { byte[] bytes = new byte[item.remaining()]; item.get(bytes); output.writeBytes(bytes); }
                public void onError(Throwable throwable) { throw new RuntimeException(throwable); }
                public void onComplete() {}
            });
            return output.toString(StandardCharsets.UTF_8);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(1)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            if (failure != null) return CompletableFuture.failedFuture(failure);
            requests.add(request);
            try {
                bodies.add(readBody(request));
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
            StubResponse next = responses.remove();
            HttpResponse.BodySubscriber<T> subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return next.status(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                @Override public Version version() { return Version.HTTP_1_1; }
            });
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long n) {}
                @Override public void cancel() {}
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(next.body().getBytes(StandardCharsets.UTF_8))));
            subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().thenApply(body ->
                    StreamingHttpClient.response(request, next.status(), body));
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return sendAsync(request, handler); }
    }

    private record StubResponse(int status, String body) {
        HttpResponse<java.io.InputStream> response(HttpRequest request) {
            return new HttpResponse<>() {
                @Override public int statusCode() { return status; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<java.io.InputStream>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (a, b) -> true); }
                @Override public java.io.InputStream body() { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            };
        }
    }

    private static final class StreamingHttpClient extends HttpClient {
        private final boolean drip;
        private final CountDownLatch cancelled = new CountDownLatch(1);

        private StreamingHttpClient(boolean drip) { this.drip = drip; }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> handler) {
            HttpResponse.BodySubscriber<T> subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return 200; }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                @Override public Version version() { return Version.HTTP_1_1; }
            });
            AtomicBoolean stopped = new AtomicBoolean();
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long n) {}
                @Override public void cancel() { stopped.set(true); cancelled.countDown(); }
            });
            if (drip) {
                Thread.ofVirtual().start(() -> {
                    byte[] bytes = "{\"sub\":\"subject\",\"name\":\"Drip User\"}".getBytes(StandardCharsets.UTF_8);
                    for (byte value : bytes) {
                        if (stopped.get()) return;
                        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{value})));
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    subscriber.onComplete();
                });
            }
            CompletableFuture<HttpResponse<T>> result = subscriber.getBody().toCompletableFuture()
                    .thenApply(body -> response(request, body));
            result.whenComplete((ignored, failure) -> {
                if (result.isCancelled()) {
                    stopped.set(true);
                    cancelled.countDown();
                }
            });
            return result;
        }

        private static <T> HttpResponse<T> response(HttpRequest request, T body) {
            return response(request, 200, body);
        }

        private static <T> HttpResponse<T> response(HttpRequest request, int status, T body) {
            return new HttpResponse<>() {
                @Override public int statusCode() { return status; }
                @Override public HttpRequest request() { return request; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                @Override public T body() { return body; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return request.uri(); }
                @Override public Version version() { return Version.HTTP_1_1; }
            };
        }

        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new AssertionError("OAuth requests must use an asynchronous total-operation deadline");
        }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, handler);
        }
    }
}
