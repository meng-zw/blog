package com.blog.media.storage.cloudreve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Secret-safe HTTP client for the Cloudreve v4 OAuth contracts. */
@Component
public class CloudreveOAuthClient {
    static final String REQUESTED_SCOPE = "openid profile offline_access Files.Read Files.Write";
    private static final Set<String> REQUIRED_SCOPES = Set.of("offline_access", "Files.Read", "Files.Write");
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    // Cloudreve v4's refresh endpoint maps an unusable refresh token to serializer.CodeCredentialInvalid.
    private static final int CLOUDREVE_CREDENTIAL_INVALID = 40020;

    private final CloudreveProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CloudreveOAuthClient(CloudreveProperties properties, ObjectMapper objectMapper) {
        this(properties, HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                objectMapper, Clock.systemUTC());
    }

    CloudreveOAuthClient(CloudreveProperties properties, HttpClient httpClient, ObjectMapper objectMapper, Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public URI authorizationUri(String state, String codeVerifier) {
        String challenge;
        try {
            challenge = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(requireText(codeVerifier, "PKCE verifier is required").getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        String query = "response_type=code"
                + "&client_id=" + encode(properties.getClientId())
                + "&redirect_uri=" + encode(properties.getRedirectUri().toString())
                + "&scope=" + encode(REQUESTED_SCOPE)
                + "&state=" + encode(requireText(state, "OAuth state is required"))
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256";
        URI endpoint = properties.authorizationUri();
        String separator = endpoint.getRawQuery() == null ? "?" : "&";
        return URI.create(endpoint + separator + query);
    }

    public TokenPair exchangeCode(String code, String verifier) {
        String form = "grant_type=authorization_code"
                + "&client_id=" + encode(properties.getClientId())
                + "&client_secret=" + encode(properties.getClientSecret())
                + "&code=" + encode(requireText(code, "Authorization code is required"))
                + "&code_verifier=" + encode(requireText(verifier, "PKCE verifier is required"));
        JsonNode json = send(HttpRequest.newBuilder(properties.tokenUri())
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build());
        if (!"Bearer".equalsIgnoreCase(text(json, "token_type"))) {
            throw new OAuthProtocolException("Cloudreve returned an unsupported token type");
        }
        Set<String> scopes = parseScopes(text(json, "scope"));
        requireScopes(scopes);
        String refresh = text(json, "refresh_token");
        if (refresh.isBlank()) throw new OAuthProtocolException("Cloudreve did not issue an offline token");
        return new TokenPair(textRequired(json, "access_token"), refresh,
                clock.instant().plusSeconds(positiveLong(json, "expires_in")),
                clock.instant().plusSeconds(positiveLong(json, "refresh_token_expires_in")), List.copyOf(scopes));
    }

    public TokenPair refresh(String refreshToken, List<String> existingScopes) {
        return refresh(refreshToken, existingScopes, properties.getRequestTimeout());
    }

    TokenPair refresh(String refreshToken, List<String> existingScopes, Duration operationTimeout) {
        requireScopes(new LinkedHashSet<>(existingScopes));
        String body;
        try {
            body = objectMapper.writeValueAsString(java.util.Map.of(
                    "refresh_token", requireText(refreshToken, "Refresh token is required")));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode Cloudreve refresh request", exception);
        }
        JsonNode envelope = send(HttpRequest.newBuilder(properties.refreshUri())
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), boundedTimeout(operationTimeout));
        if (envelope.path("code").asInt(-1) != 0) {
            if (isInvalidGrant(envelope)) throw new InvalidGrantException();
            throw new OAuthUnavailableException("Cloudreve refresh was rejected");
        }
        JsonNode data = envelope.path("data");
        return new TokenPair(textRequired(data, "access_token"), textRequired(data, "refresh_token"),
                instant(data, "access_expires"), instant(data, "refresh_expires"), List.copyOf(existingScopes));
    }

    public UserInfo userInfo(String accessToken) {
        JsonNode json = send(HttpRequest.newBuilder(properties.userInfoUri())
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + requireText(accessToken, "Access token is required"))
                .GET().build());
        String subject = textRequired(json, "sub");
        String displayName = firstText(json, "name", "preferred_username", "sub");
        return new UserInfo(subject, displayName);
    }

    private JsonNode send(HttpRequest request) {
        return send(request, properties.getRequestTimeout());
    }

    private JsonNode send(HttpRequest request, Duration operationTimeout) {
        CompletableFuture<HttpResponse<byte[]>> operation;
        try {
            operation = httpClient.sendAsync(request, ignored -> new LimitedBodySubscriber(MAX_RESPONSE_BYTES));
        } catch (RuntimeException exception) {
            throw new OAuthUnavailableException("Cloudreve request failed");
        }
        HttpResponse<byte[]> response;
        try {
            response = operation.get(operationTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            operation.cancel(true);
            throw new OAuthUnavailableException("Cloudreve request timed out");
        } catch (InterruptedException exception) {
            operation.cancel(true);
            Thread.currentThread().interrupt();
            throw new OAuthUnavailableException("Cloudreve request was interrupted");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof OAuthProtocolException protocol) throw protocol;
            throw new OAuthUnavailableException("Cloudreve request failed");
        }
        if (response.statusCode() >= 500) {
            throw new OAuthUnavailableException("Cloudreve returned HTTP " + response.statusCode());
        }
        JsonNode json = parseJson(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (isInvalidGrant(json)) throw new InvalidGrantException();
            throw new OAuthUnavailableException("Cloudreve returned HTTP " + response.statusCode());
        }
        return json;
    }

    private Duration boundedTimeout(Duration requested) {
        if (requested == null || requested.isZero() || requested.isNegative()) {
            throw new OAuthUnavailableException("Cloudreve request deadline expired");
        }
        return requested.compareTo(properties.getRequestTimeout()) < 0
                ? requested : properties.getRequestTimeout();
    }

    private JsonNode parseJson(byte[] bytes) {
        try {
            JsonNode json = objectMapper.readTree(bytes);
            if (json == null || !json.isObject()) {
                throw new OAuthProtocolException("Cloudreve returned an invalid response");
            }
            return json;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof OAuthProtocolException protocol) throw protocol;
            throw new OAuthProtocolException("Cloudreve returned an invalid response");
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

        private LimitedBodySubscriber(int limit) { this.limit = limit; }

        @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return body; }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = Objects.requireNonNull(subscription);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                int next = buffer.remaining();
                if (next > limit - received) {
                    subscription.cancel();
                    body.completeExceptionally(new OAuthProtocolException("Cloudreve response exceeded the limit"));
                    return;
                }
                byte[] bytes = new byte[next];
                buffer.get(bytes);
                output.writeBytes(bytes);
                received += next;
            }
        }

        @Override public void onError(Throwable throwable) { body.completeExceptionally(throwable); }
        @Override public void onComplete() { body.complete(output.toByteArray()); }
    }

    private static boolean isInvalidGrant(JsonNode json) {
        return "invalid_grant".equals(json.path("error").asText())
                || "invalid_grant".equals(json.path("data").path("error").asText())
                || json.path("code").asInt(-1) == CLOUDREVE_CREDENTIAL_INVALID;
    }

    private static void requireScopes(Set<String> scopes) {
        if (!scopes.containsAll(REQUIRED_SCOPES)) {
            throw new OAuthProtocolException("Cloudreve did not grant the required scopes");
        }
    }

    private static Set<String> parseScopes(String scope) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Arrays.stream(scope.trim().split("\\s+")).filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    private static String firstText(JsonNode json, String... names) {
        for (String name : names) {
            String value = text(json, name);
            if (!value.isBlank()) return value;
        }
        throw new OAuthProtocolException("Cloudreve user information was incomplete");
    }

    private static String textRequired(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) throw new OAuthProtocolException("Cloudreve response omitted a required field");
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static long positiveLong(JsonNode node, String field) {
        long value = node.path(field).asLong(0);
        if (value <= 0) throw new OAuthProtocolException("Cloudreve response contained an invalid expiry");
        return value;
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(textRequired(node, field));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new OAuthProtocolException("Cloudreve response contained an invalid expiry");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(requireText(value, "OAuth parameter is required"), StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value;
    }

    public record TokenPair(String accessToken, String refreshToken, Instant accessExpiresAt,
                            Instant refreshExpiresAt, List<String> scopes) {
        @Override public String toString() {
            return "TokenPair[accessToken=redacted, refreshToken=redacted, accessExpiresAt=" + accessExpiresAt
                    + ", refreshExpiresAt=" + refreshExpiresAt + ", scopes=" + scopes + "]";
        }
    }

    public record UserInfo(String subject, String displayName) {}

    public static class OAuthProtocolException extends RuntimeException {
        OAuthProtocolException(String message) { super(message); }
    }

    public static class OAuthUnavailableException extends RuntimeException {
        OAuthUnavailableException(String message) { super(message); }
    }

    public static final class InvalidGrantException extends RuntimeException {
        InvalidGrantException() { super("Cloudreve authorization grant is no longer valid"); }
    }
}
