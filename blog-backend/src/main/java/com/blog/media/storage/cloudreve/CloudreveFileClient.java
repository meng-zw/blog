package com.blog.media.storage.cloudreve;

import com.blog.media.storage.ObjectUploadRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Secret-safe, bounded Cloudreve v4 file and upload client. */
@Component
@Conditional(CloudreveConfiguration.CloudreveRequiredConfigurationCondition.class)
public class CloudreveFileClient {
    private static final int MAX_JSON_BYTES = 64 * 1024;
    private static final int MAX_PROVIDER_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_CHUNK_BYTES = 32 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final String MIME_METADATA_KEY = "blog:mime_type";
    private static final Set<Integer> CONFLICT_CODES = Set.of(409, 40004);

    private final CloudreveProperties properties;
    private final CloudreveTokenService tokens;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Clock clock;
    private final URI apiOrigin;
    private final Set<URI> allowedOrigins;

    @Autowired
    public CloudreveFileClient(CloudreveProperties properties,
                               CloudreveTokenService tokens,
                               ObjectMapper json) {
        this(properties, tokens, HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                json, Clock.systemUTC());
    }

    CloudreveFileClient(CloudreveProperties properties,
                        CloudreveTokenService tokens,
                        HttpClient http,
                        ObjectMapper json,
                        Clock clock) {
        this.properties = Objects.requireNonNull(properties, "Cloudreve properties are required");
        this.tokens = Objects.requireNonNull(tokens, "Cloudreve token service is required");
        this.http = Objects.requireNonNull(http, "HTTP client is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        validateTimeout(properties.getRequestTimeout());
        this.apiOrigin = origin(requireAbsoluteHttp(properties.getBaseUrl(), "Cloudreve base URL"));
        java.util.LinkedHashSet<URI> origins = new java.util.LinkedHashSet<>();
        origins.add(apiOrigin);
        for (URI configured : properties.getProviderOrigins()) {
            URI candidate = origin(requireAbsoluteHttp(configured, "Cloudreve provider origin"));
            if (!properties.isAllowTrustedInternalHttp() && !"https".equalsIgnoreCase(candidate.getScheme())) {
                throw new IllegalArgumentException("Cloudreve provider origins must use HTTPS");
            }
            origins.add(candidate);
        }
        this.allowedOrigins = Set.copyOf(origins);
    }

    public CloudreveFileMetadata upload(String path, ObjectUploadRequest request, InputStream content) {
        Objects.requireNonNull(request, "Object upload request is required");
        Objects.requireNonNull(content, "Object content is required");
        String key = requirePath(path);
        if (!key.equals(request.objectKey())) {
            throw new IllegalArgumentException("Object request key does not match the Cloudreve path");
        }
        String uri = fileUri(key);
        createParentDirectory(uri);
        CloudreveUploadSession session = null;
        boolean finalized = false;
        try {
            session = createUploadSession(uri, request);
            validateSession(session, uri, request.byteSize());
            List<String> etags = uploadChunks(session, request, content);
            if (usesS3Multipart(session)) {
                completeS3Multipart(session, etags);
                sendUploadCallback(endpoint("/api/v4/callback/" + pathSegment(session.policyType()) + "/"
                        + pathSegment(session.id()) + "/" + pathSegment(session.callbackSecret())));
            }
            finalized = true;
            return inspect(key);
        } catch (CloudreveApiException failure) {
            if (session != null && !finalized) abortQuietly(session);
            throw failure;
        } catch (IOException failure) {
            if (session != null && !finalized) abortQuietly(session);
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve upload stream failed", failure);
        } catch (RuntimeException failure) {
            if (session != null && !finalized) abortQuietly(session);
            if (failure instanceof IllegalArgumentException) throw failure;
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve upload failed", failure);
        }
    }

    public CloudreveFileMetadata inspect(String path) {
        String uri = fileUri(requirePath(path));
        URI endpoint = endpoint("/api/v4/file/info?uri=" + encodeQuery(uri));
        JsonNode data = sendApi("GET", endpoint, null, true, true);
        try {
            if (data.path("type").asInt(-1) != 0) throw malformed();
            String returnedPath = requiredText(data, "path");
            if (!uri.equals(returnedPath)) throw malformed();
            JsonNode metadata = data.path("metadata");
            if (!metadata.isObject()) throw malformed();
            long size = requiredNonNegativeLong(data, "size");
            return new CloudreveFileMetadata(returnedPath, requiredText(data, "id"),
                    requiredText(metadata, MIME_METADATA_KEY), size, requiredText(data, "primary_entity"));
        } catch (CloudreveApiException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw malformed();
        }
    }

    public InputStream open(String path) {
        String uri = fileUri(requirePath(path));
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("uris", List.of(uri));
        request.put("download", false);
        request.put("redirect", false);
        request.put("archive", false);
        request.put("no_cache", true);
        JsonNode data = sendApi("POST", endpoint("/api/v4/file/url"), request, true, true);
        JsonNode urls = data.path("urls");
        if (!urls.isArray() || urls.size() != 1) throw malformed();
        URI contentUri = parseAllowedUri(requiredText(urls.get(0), "url"));
        return openContent(contentUri);
    }

    public void delete(String path) {
        String uri = fileUri(requirePath(path));
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("uris", List.of(uri));
        request.put("unlink", false);
        request.put("skip_soft_delete", true);
        sendApi("DELETE", endpoint("/api/v4/file"), request, false, true);
    }

    private void createParentDirectory(String fileUri) {
        int separator = fileUri.lastIndexOf('/');
        if (separator <= "cloudreve://my".length()) throw new IllegalArgumentException("Cloudreve file needs a parent directory");
        LinkedHashMap<String, Object> request = new LinkedHashMap<>();
        request.put("type", "folder");
        request.put("uri", fileUri.substring(0, separator));
        request.put("err_on_conflict", false);
        sendApi("POST", endpoint("/api/v4/file/create"), request, true, true, true);
    }

    private CloudreveUploadSession createUploadSession(String uri, ObjectUploadRequest request) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("uri", uri);
        body.put("size", request.byteSize());
        body.put("mime_type", request.contentType());
        body.put("metadata", Map.of(MIME_METADATA_KEY, request.contentType()));
        JsonNode data = sendApi("PUT", endpoint("/api/v4/file/upload"), body, true, true);
        JsonNode policy = data.path("storage_policy");
        List<URI> uploadUrls = new ArrayList<>();
        JsonNode rawUrls = data.path("upload_urls");
        if (rawUrls.isArray()) {
            for (JsonNode rawUrl : rawUrls) uploadUrls.add(parseAbsoluteUri(rawUrl.asText()));
        }
        String completion = optionalText(data, "completeURL");
        return new CloudreveUploadSession(
                requiredText(data, "session_id"),
                requiredPositiveLong(data, "chunk_size"),
                Instant.ofEpochSecond(requiredPositiveLong(data, "expires")),
                requiredText(policy, "type").toLowerCase(Locale.ROOT),
                policy.path("relay").asBoolean(false),
                uploadUrls,
                optionalText(data, "credential"),
                completion == null ? null : parseAbsoluteUri(completion),
                optionalText(data, "callback_secret"),
                requiredText(data, "uri"));
    }

    private void validateSession(CloudreveUploadSession session, String expectedUri, long byteSize) {
        if (session.chunkSize() <= 0 || session.chunkSize() > MAX_CHUNK_BYTES || session.expiresAt() == null
                || !session.expiresAt().isAfter(clock.instant()) || session.id() == null || session.id().isBlank()
                || !expectedUri.equals(session.fileUri())) {
            throw malformed();
        }
        if (byteSize > 0 && session.chunkSize() > Math.max(byteSize, MAX_CHUNK_BYTES)) throw malformed();
        long chunks = Math.max(1L, (byteSize + session.chunkSize() - 1L) / session.chunkSize());
        if (usesRelay(session)) {
            if (!session.uploadUrls().isEmpty()) session.uploadUrls().forEach(this::requireAllowed);
            return;
        }
        if ("remote".equals(session.policyType())) {
            if (session.uploadUrls().size() != 1 || session.credential() == null || session.credential().isBlank()) {
                throw malformed();
            }
            requireAllowed(session.uploadUrls().getFirst());
            return;
        }
        if (usesS3Multipart(session)) {
            if (session.uploadUrls().size() != chunks || session.completionUrl() == null
                    || session.callbackSecret() == null || session.callbackSecret().isBlank()) {
                throw malformed();
            }
            session.uploadUrls().forEach(this::requireAllowed);
            requireAllowed(session.completionUrl());
            return;
        }
        throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve storage policy is unsupported", null);
    }

    private List<String> uploadChunks(CloudreveUploadSession session,
                                      ObjectUploadRequest request,
                                      InputStream content) throws IOException {
        int bufferSize = (int) Math.min(session.chunkSize(), Math.max(1L, request.byteSize()));
        byte[] buffer = new byte[bufferSize];
        long sent = 0;
        int index = 0;
        List<String> etags = new ArrayList<>();
        do {
            int expected = (int) Math.min(session.chunkSize(), request.byteSize() - sent);
            int read = readExactly(content, buffer, expected);
            if (read != expected) {
                throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve upload content length did not match", null);
            }
            String etag = uploadChunk(session, index, buffer, read);
            if (usesS3Multipart(session)) {
                if (etag == null || etag.isBlank()) throw malformed();
                etags.add(etag);
            }
            sent += read;
            index++;
        } while (sent < request.byteSize() || (request.byteSize() == 0 && index == 0));
        if (content.read() != -1) {
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve upload content exceeded its declared size", null);
        }
        return etags;
    }

    private String uploadChunk(CloudreveUploadSession session, int index, byte[] buffer, int length) {
        URI target;
        String authorization = null;
        String method;
        if (usesRelay(session)) {
            target = endpoint("/api/v4/file/upload/" + pathSegment(session.id()) + "/" + index);
            authorization = "Bearer " + accessToken();
            method = "POST";
        } else if ("remote".equals(session.policyType())) {
            URI base = session.uploadUrls().getFirst();
            String separator = base.getRawQuery() == null ? "?" : "&";
            target = URI.create(base + separator + "chunk=" + index);
            authorization = session.credential();
            method = "POST";
        } else {
            target = session.uploadUrls().get(index);
            method = "PUT";
        }
        RawResponse response = sendRaw(method, target, authorization, buffer, length, MAX_PROVIDER_RESPONSE_BYTES);
        if (response.status() < 200 || response.status() >= 300) throw translateHttp(response.status());
        if (usesRelay(session) || "remote".equals(session.policyType())) {
            try {
                parseEnvelope(response.body(), false, false);
            } catch (UnauthorizedFailure unauthorized) {
                throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve upload authorization was rejected", null);
            }
        }
        return firstHeader(response.headers(), "ETag");
    }

    private void completeS3Multipart(CloudreveUploadSession session, List<String> etags) {
        StringBuilder xml = new StringBuilder("<CompleteMultipartUpload>");
        for (int index = 0; index < etags.size(); index++) {
            xml.append("<Part><PartNumber>").append(index + 1).append("</PartNumber><ETag>")
                    .append(xmlEscape(etags.get(index))).append("</ETag></Part>");
        }
        xml.append("</CompleteMultipartUpload>");
        byte[] bytes = xml.toString().getBytes(StandardCharsets.UTF_8);
        RawResponse response = sendRaw("POST", session.completionUrl(), null, bytes, bytes.length,
                MAX_PROVIDER_RESPONSE_BYTES);
        if (response.status() < 200 || response.status() >= 300) throw translateHttp(response.status());
    }

    private void abortQuietly(CloudreveUploadSession session) {
        try {
            sendApi("DELETE", endpoint("/api/v4/file/upload"),
                    Map.of("id", session.id(), "uri", session.fileUri()), false, true);
        } catch (RuntimeException ignored) {
            // The original upload failure remains authoritative.
        }
    }

    private void sendUploadCallback(URI target) {
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .GET().build();
        RawResponse response = sendBounded(request, MAX_JSON_BYTES);
        if (response.status() < 200 || response.status() >= 300) throw translateHttp(response.status());
        try {
            parseEnvelope(response.body(), false, false);
        } catch (UnauthorizedFailure unauthorized) {
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve upload callback was rejected", null);
        }
    }

    private InputStream openContent(URI initial) {
        URI current = initial;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            requireAllowed(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "*/*")
                    .GET().build();
            HttpResponse<InputStream> response = sendStream(request);
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                InputStream body = response.body();
                return new FilterInputStream(body) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                    }
                };
            }
            try {
                response.body().close();
            } catch (IOException ignored) {
            }
            if (status >= 300 && status < 400 && redirect < MAX_REDIRECTS) {
                String location = response.headers().firstValue("Location").orElseThrow(CloudreveFileClient::malformed);
                current = current.resolve(location);
                requireAllowed(current);
                continue;
            }
            throw translateHttp(status);
        }
        throw malformed();
    }

    private JsonNode sendApi(String method, URI target, Object body, boolean requireData, boolean retryUnauthorized) {
        return sendApi(method, target, body, requireData, retryUnauthorized, false);
    }

    private JsonNode sendApi(String method, URI target, Object body, boolean requireData,
                             boolean retryUnauthorized, boolean allowConflict) {
        int attempts = retryUnauthorized ? 2 : 1;
        String token = accessToken();
        for (int attempt = 0; attempt < attempts; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                byte[] encoded = encodeJson(body);
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(encoded));
            }
            RawResponse response = sendBounded(builder.build(), MAX_JSON_BYTES);
            if (response.status() == 401 && attempt + 1 < attempts) {
                token = accessTokenAfterRejection(token);
                continue;
            }
            if (response.status() < 200 || response.status() >= 300) throw translateHttp(response.status());
            try {
                return parseEnvelope(response.body(), requireData, allowConflict);
            } catch (UnauthorizedFailure unauthorized) {
                if (attempt + 1 < attempts) {
                    token = accessTokenAfterRejection(token);
                    continue;
                }
                throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve authorization was rejected", null);
            }
        }
        throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve authorization was rejected", null);
    }

    private String accessToken() {
        try {
            String token = tokens.validAccessToken();
            if (token == null || token.isBlank()) {
                throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve authorization is unavailable", null);
            }
            return token;
        } catch (CloudreveApiException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve authorization is unavailable", failure);
        }
    }

    private String accessTokenAfterRejection(String rejectedAccessToken) {
        try {
            String token = tokens.validAccessTokenAfterRejection(rejectedAccessToken);
            if (token == null || token.isBlank()) {
                throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve authorization is unavailable", null);
            }
            return token;
        } catch (CloudreveApiException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve authorization is unavailable", failure);
        }
    }

    private JsonNode parseEnvelope(byte[] body, boolean requireData, boolean allowConflict) {
        JsonNode envelope;
        try {
            envelope = json.readTree(body);
        } catch (IOException | RuntimeException failure) {
            throw malformed();
        }
        if (envelope == null || !envelope.isObject() || !envelope.has("code") || !envelope.path("code").canConvertToInt()) {
            throw malformed();
        }
        int code = envelope.path("code").asInt();
        if (code == 401) throw new UnauthorizedFailure();
        if (allowConflict && CONFLICT_CODES.contains(code)) return envelope.path("data");
        if (code != 0) throw translateCode(code);
        JsonNode data = envelope.path("data");
        if (requireData && (data.isMissingNode() || data.isNull())) throw malformed();
        return data;
    }

    private RawResponse sendRaw(String method, URI target, String authorization,
                                byte[] body, int length, int responseLimit) {
        requireAllowed(target);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/octet-stream");
        if (authorization != null) builder.header("Authorization", authorization);
        builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body, 0, length));
        RawResponse response = sendBounded(builder.build(), responseLimit);
        if (response.status() >= 300 && response.status() < 400) {
            String location = firstHeader(response.headers(), "Location");
            if (location == null) throw malformed();
            requireAllowed(target.resolve(location));
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve upload redirect was refused", null);
        }
        return response;
    }

    private RawResponse sendBounded(HttpRequest request, int responseLimit) {
        CompletableFuture<HttpResponse<byte[]>> operation;
        try {
            operation = http.sendAsync(request, ignored -> new LimitedBodySubscriber(responseLimit));
        } catch (RuntimeException failure) {
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve request failed", failure);
        }
        try {
            HttpResponse<byte[]> response = operation.get(properties.getRequestTimeout().toNanos(), TimeUnit.NANOSECONDS);
            return new RawResponse(response.statusCode(), response.headers().map(), response.body());
        } catch (TimeoutException failure) {
            operation.cancel(true);
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve request timed out", failure);
        } catch (InterruptedException failure) {
            operation.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve request was interrupted", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ResponseTooLargeException) throw malformed();
            if (cause instanceof CloudreveApiException typed) throw typed;
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve request failed", cause);
        }
    }

    private HttpResponse<InputStream> sendStream(HttpRequest request) {
        CompletableFuture<HttpResponse<InputStream>> operation;
        try {
            operation = http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (RuntimeException failure) {
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve content request failed", failure);
        }
        try {
            return operation.get(properties.getRequestTimeout().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException failure) {
            operation.cancel(true);
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve content request timed out", failure);
        } catch (InterruptedException failure) {
            operation.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve content request was interrupted", failure);
        } catch (ExecutionException failure) {
            throw failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve content request failed", failure.getCause());
        }
    }

    private byte[] encodeJson(Object body) {
        try {
            return json.writeValueAsBytes(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Could not encode Cloudreve request", failure);
        }
    }

    private URI endpoint(String pathAndQuery) {
        return properties.getBaseUrl().resolve(pathAndQuery);
    }

    private String fileUri(String relativePath) {
        String root = properties.getRootPath();
        if (root == null || root.isBlank() || !root.startsWith("/")) {
            throw new IllegalArgumentException("Cloudreve root path is invalid");
        }
        String combined = ("/".equals(root) ? "" : root) + "/" + relativePath;
        try {
            return new URI("cloudreve", "my", combined, null).toASCIIString();
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Cloudreve path is invalid", failure);
        }
    }

    private static String requirePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Cloudreve path is invalid");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Cloudreve path is invalid");
            }
        }
        return path;
    }

    private URI parseAllowedUri(String value) {
        URI uri = parseAbsoluteUri(value);
        requireAllowed(uri);
        return uri;
    }

    private URI parseAbsoluteUri(String value) {
        try {
            return requireAbsoluteHttp(URI.create(value), "Cloudreve URL");
        } catch (IllegalArgumentException failure) {
            throw malformed();
        }
    }

    private void requireAllowed(URI uri) {
        if (!allowedOrigins.contains(origin(requireAbsoluteHttp(uri, "Cloudreve URL")))) {
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve returned an untrusted origin", null);
        }
    }

    private static URI requireAbsoluteHttp(URI uri, String name) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getUserInfo() != null || uri.getFragment() != null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(name + " must be an absolute HTTP(S) URI");
        }
        return uri;
    }

    private static URI origin(URI uri) {
        try {
            int port = uri.getPort();
            if (port == -1) port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null,
                    uri.getHost().toLowerCase(Locale.ROOT), port, null, null, null);
        } catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("Cloudreve origin is invalid", impossible);
        }
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String pathSegment(String value) {
        if (value == null || value.isBlank()) throw malformed();
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static int readExactly(InputStream content, byte[] buffer, int length) throws IOException {
        int offset = 0;
        while (offset < length) {
            int count = content.read(buffer, offset, length - offset);
            if (count < 0) break;
            if (count == 0) {
                int single = content.read();
                if (single < 0) break;
                buffer[offset++] = (byte) single;
            } else {
                offset += count;
            }
        }
        return offset;
    }

    private static boolean usesRelay(CloudreveUploadSession session) {
        return session.relay() || "local".equals(session.policyType());
    }

    private static boolean usesS3Multipart(CloudreveUploadSession session) {
        return !session.relay() && ("s3".equals(session.policyType()) || "ks3".equals(session.policyType()));
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw malformed();
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual() || value.asText().isBlank()) throw malformed();
        return value.asText();
    }

    private static long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) throw malformed();
        return value.asLong();
    }

    private static long requiredNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) throw malformed();
        return value.asLong();
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase(name) && !header.getValue().isEmpty()) return header.getValue().getFirst();
        }
        return null;
    }

    private static CloudreveApiException translateHttp(int status) {
        if (status == 404) return failure(CloudreveApiException.Kind.NOT_FOUND, "Cloudreve object was not found", null);
        if (status == 409) return failure(CloudreveApiException.Kind.CONFLICT, "Cloudreve object conflicts with existing state", null);
        if (status == 401 || status == 429 || status >= 500) {
            return failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve request is temporarily unavailable", null);
        }
        return failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve request was rejected", null);
    }

    private static CloudreveApiException translateCode(int code) {
        if (code == 404) return failure(CloudreveApiException.Kind.NOT_FOUND, "Cloudreve object was not found", null);
        if (CONFLICT_CODES.contains(code)) {
            return failure(CloudreveApiException.Kind.CONFLICT, "Cloudreve object conflicts with existing state", null);
        }
        if (code == 429 || code >= 500 && code < 600) {
            return failure(CloudreveApiException.Kind.TRANSIENT, "Cloudreve request is temporarily unavailable", null);
        }
        return failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve request failed", null);
    }

    private static CloudreveApiException malformed() {
        return failure(CloudreveApiException.Kind.PROVIDER_FAILURE, "Cloudreve returned an invalid response", null);
    }

    private static CloudreveApiException failure(CloudreveApiException.Kind kind, String message, Throwable cause) {
        return new CloudreveApiException(kind, message, cause);
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Cloudreve request timeout must be positive");
        }
    }

    private record RawResponse(int status, Map<String, List<String>> headers, byte[] body) {
    }

    private static final class UnauthorizedFailure extends RuntimeException {
    }

    private static final class ResponseTooLargeException extends RuntimeException {
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

        private LimitedBodySubscriber(int limit) {
            this.limit = limit;
        }

        @Override
        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return body;
        }

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
                    body.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[next];
                buffer.get(bytes);
                output.writeBytes(bytes);
                received += next;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }
}
