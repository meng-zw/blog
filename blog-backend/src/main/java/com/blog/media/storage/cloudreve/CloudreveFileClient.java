package com.blog.media.storage.cloudreve;

import com.blog.media.MediaProperties;
import com.blog.media.storage.ObjectUploadRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.ArrayDeque;
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
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Secret-safe, bounded Cloudreve v4 file and upload client. */
@Component
@Conditional(CloudreveConfiguration.CloudreveRequiredConfigurationCondition.class)
/** Cloudreve v4 文件 API 适配层：屏蔽 OAuth、分片协议及错误映射，业务层只处理统一存储契约。 */
public class CloudreveFileClient {
    private static final int MAX_JSON_BYTES = 64 * 1024;
    private static final int MAX_PROVIDER_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_CHUNK_BYTES = 32 * 1024 * 1024;
    private static final int MIN_S3_NON_FINAL_PART_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final String MIME_METADATA_KEY = "blog:mime_type";
    private static final Set<Integer> CONFLICT_CODES = Set.of(409, 40004);

    private final CloudreveProperties properties;
    private final long maximumDownloadBytes;
    private final CloudreveTokenService tokens;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Clock clock;
    private final URI apiOrigin;
    private final Set<URI> allowedOrigins;

    @Autowired
    public CloudreveFileClient(CloudreveProperties properties,
                               MediaProperties mediaProperties,
                               CloudreveTokenService tokens,
                               ObjectMapper json) {
        this(properties, mediaProperties, tokens, HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                json, Clock.systemUTC());
    }

    CloudreveFileClient(CloudreveProperties properties,
                        MediaProperties mediaProperties,
                        CloudreveTokenService tokens,
                        HttpClient http,
                        ObjectMapper json,
                        Clock clock) {
        this.properties = Objects.requireNonNull(properties, "Cloudreve properties are required");
        Objects.requireNonNull(mediaProperties, "Media properties are required");
        this.maximumDownloadBytes = Math.max(mediaProperties.getMaxBytes(),
                Math.max(mediaProperties.getMaxAttachmentBytes(), mediaProperties.getMaxZipAttachmentBytes()));
        if (maximumDownloadBytes <= 0) {
            throw new IllegalArgumentException("Media download maximum must be positive");
        }
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
        createParentDirectories(key);
        CloudreveUploadSession session = null;
        boolean finalized = false;
        try {
            session = createUploadSession(uri, request);
            validateSession(session, uri, request);
            List<String> etags = uploadChunks(session, request, content);
            if (usesS3Multipart(session)) {
                completeS3Multipart(session, etags);
                sendUploadCallback(uploadCallbackEndpoint("/" + pathSegment(session.policyType()) + "/"
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
        URI endpoint = apiEndpoint("/file/info?uri=" + encodeQuery(uri));
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
        JsonNode data = sendApi("POST", apiEndpoint("/file/url"), request, true, true);
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
        sendApi("DELETE", apiEndpoint("/file"), request, false, true);
    }

    private void createParentDirectories(String relativePath) {
        String root = properties.getRootPath();
        String parent = relativePath.substring(0, relativePath.lastIndexOf('/'));
        String combined = ("/".equals(root) ? "" : root) + "/" + parent;
        StringBuilder path = new StringBuilder();
        for (String segment : combined.split("/")) {
            if (segment.isEmpty()) continue;
            path.append('/').append(segment);
            LinkedHashMap<String, Object> request = new LinkedHashMap<>();
            request.put("type", "folder");
            request.put("uri", logicalUri(path.toString()));
            request.put("err_on_conflict", false);
            sendApi("POST", apiEndpoint("/file/create"), request, false, true, true);
        }
    }

    private CloudreveUploadSession createUploadSession(String uri, ObjectUploadRequest request) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("uri", uri);
        body.put("size", request.byteSize());
        body.put("policy_id", properties.getPolicyId());
        body.put("mime_type", request.contentType());
        body.put("metadata", Map.of(MIME_METADATA_KEY, request.contentType()));
        JsonNode data = sendApi("PUT", apiEndpoint("/file/upload"), body, true, true);
        String recoverableSessionId = recoverableText(data, "session_id");
        try {
            JsonNode policy = data.path("storage_policy");
            List<URI> uploadUrls = new ArrayList<>();
            JsonNode rawUrls = data.path("upload_urls");
            if (rawUrls.isArray()) {
                for (JsonNode rawUrl : rawUrls) uploadUrls.add(parseAbsoluteUri(rawUrl.asText()));
            }
            String completion = optionalText(data, "completeURL");
            return new CloudreveUploadSession(
                    requiredText(data, "session_id"),
                    requiredNonNegativeLong(data, "chunk_size"),
                    Instant.ofEpochSecond(requiredPositiveLong(data, "expires")),
                    requiredText(policy, "id"),
                    requiredText(policy, "type").toLowerCase(Locale.ROOT),
                    policy.path("relay").asBoolean(false),
                    uploadUrls,
                    optionalText(data, "credential"),
                    completion == null ? null : parseAbsoluteUri(completion),
                    optionalText(data, "callback_secret"),
                    requiredText(data, "uri"));
        } catch (RuntimeException malformedSession) {
            if (recoverableSessionId != null) abortQuietly(recoverableSessionId, uri);
            if (malformedSession instanceof CloudreveApiException typed) throw typed;
            throw malformed();
        }
    }

    private void validateSession(CloudreveUploadSession session, String expectedUri, ObjectUploadRequest request) {
        long byteSize = request.byteSize();
        long effectiveChunkSize = effectiveChunkSize(session, byteSize);
        if (session.chunkSize() < 0 || session.chunkSize() > MAX_CHUNK_BYTES || session.expiresAt() == null
                || !session.expiresAt().isAfter(clock.instant()) || session.id() == null || session.id().isBlank()
                || !expectedUri.equals(session.fileUri())) {
            throw malformed();
        }
        if (!properties.getPolicyId().equals(session.policyId()) || !"s3".equals(session.policyType())) {
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE,
                    "Cloudreve storage policy did not match the approved S3 policy", null);
        }
        long chunks = byteSize == 0 ? 1 : 1 + (byteSize - 1) / effectiveChunkSize;
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
            if (chunks > 1 && effectiveChunkSize < MIN_S3_NON_FINAL_PART_BYTES) throw malformed();
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
        if (session.chunkSize() == 0) {
            return uploadUnchunked(session, request, content);
        }
        long effectiveChunkSize = effectiveChunkSize(session, request.byteSize());
        int bufferSize = (int) Math.min(effectiveChunkSize, Math.max(1L, request.byteSize()));
        byte[] buffer = new byte[bufferSize];
        long sent = 0;
        int index = 0;
        List<String> etags = new ArrayList<>();
        do {
            int expected = (int) Math.min(effectiveChunkSize, request.byteSize() - sent);
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

    private List<String> uploadUnchunked(CloudreveUploadSession session,
                                         ObjectUploadRequest request,
                                         InputStream content) throws IOException {
        ExactLengthInputStream exactContent = new ExactLengthInputStream(content, request.byteSize());
        HttpRequest.BodyPublisher publisher = request.byteSize() == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.fromPublisher(
                        HttpRequest.BodyPublishers.ofInputStream(() -> exactContent), request.byteSize());
        String etag;
        try {
            etag = uploadChunk(session, 0, publisher);
        } catch (CloudreveApiException failure) {
            if (exactContent.endedEarly()) {
                throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE,
                        "Cloudreve upload content length did not match", null);
            }
            throw failure;
        }
        if (!exactContent.finished()) {
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE,
                    "Cloudreve upload content length did not match", null);
        }
        if (content.read() != -1) {
            throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE,
                    "Cloudreve upload content exceeded its declared size", null);
        }
        if (!usesS3Multipart(session)) return List.of();
        if (etag == null || etag.isBlank()) throw malformed();
        return List.of(etag);
    }

    private String uploadChunk(CloudreveUploadSession session, int index, byte[] buffer, int length) {
        return uploadChunk(session, index, HttpRequest.BodyPublishers.ofByteArray(buffer, 0, length));
    }

    private String uploadChunk(CloudreveUploadSession session, int index, HttpRequest.BodyPublisher body) {
        URI target;
        String authorization = null;
        String method;
        if (usesRelay(session)) {
            target = apiEndpoint("/file/upload/" + pathSegment(session.id()) + "/" + index);
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
        RawResponse response = sendRaw(method, target, authorization, body, MAX_PROVIDER_RESPONSE_BYTES);
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
        validateS3Completion(response.body());
    }

    private void validateS3Completion(byte[] body) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(body));
            try {
                while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) {
                    // Skip the XML declaration and whitespace.
                }
                if (reader.getEventType() != XMLStreamConstants.START_ELEMENT) throw malformed();
                String root = reader.getLocalName();
                if ("Error".equals(root)) {
                    String code = null;
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamConstants.START_ELEMENT && "Code".equals(reader.getLocalName())) {
                            code = reader.getElementText();
                        }
                    }
                    if (Set.of("InternalError", "ServiceUnavailable", "SlowDown", "RequestTimeout")
                            .contains(code)) {
                        throw failure(CloudreveApiException.Kind.TRANSIENT,
                                "Cloudreve multipart completion is temporarily unavailable", null);
                    }
                    throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE,
                            "Cloudreve multipart completion was rejected", null);
                }
                if (!"CompleteMultipartUploadResult".equals(root)) throw malformed();
                java.util.HashSet<String> successFields = new java.util.HashSet<>();
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event != XMLStreamConstants.START_ELEMENT) continue;
                    String field = reader.getLocalName();
                    if (Set.of("Location", "Bucket", "Key", "ETag").contains(field)) {
                        String value = reader.getElementText();
                        if (value == null || value.isBlank()) throw malformed();
                        successFields.add(field);
                    } else if ("Error".equals(field)) {
                        throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE,
                                "Cloudreve multipart completion was rejected", null);
                    }
                }
                if (!successFields.containsAll(Set.of("Location", "Bucket", "Key", "ETag"))) throw malformed();
            } finally {
                reader.close();
            }
        } catch (CloudreveApiException failure) {
            throw failure;
        } catch (XMLStreamException | RuntimeException failure) {
            throw malformed();
        }
    }

    private void abortQuietly(CloudreveUploadSession session) {
        abortQuietly(session.id(), session.fileUri());
    }

    private void abortQuietly(String sessionId, String fileUri) {
        try {
            sendApi("DELETE", apiEndpoint("/file/upload"),
                    Map.of("id", sessionId, "uri", fileUri), false, true);
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
                return response.body();
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
            if (allowConflict && response.status() == 409) return json.missingNode();
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
        return sendRaw(method, target, authorization,
                HttpRequest.BodyPublishers.ofByteArray(body, 0, length), responseLimit);
    }

    private RawResponse sendRaw(String method, URI target, String authorization,
                                HttpRequest.BodyPublisher body, int responseLimit) {
        requireAllowed(target);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/octet-stream");
        if (authorization != null) builder.header("Authorization", authorization);
        builder.method(method, body);
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
            operation = http.sendAsync(request,
                    ignored -> new DeadlineBoundedBodySubscriber(maximumDownloadBytes, properties.getRequestTimeout()));
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

    private URI apiEndpoint(String pathAndQuery) { return properties.apiEndpoint(pathAndQuery); }

    private URI uploadCallbackEndpoint(String pathAndQuery) { return properties.uploadCallbackEndpoint(pathAndQuery); }

    private String fileUri(String relativePath) {
        String root = properties.getRootPath();
        if (root == null || root.isBlank() || !root.startsWith("/")) {
            throw new IllegalArgumentException("Cloudreve root path is invalid");
        }
        String combined = ("/".equals(root) ? "" : root) + "/" + relativePath;
        return logicalUri(combined);
    }

    private static String logicalUri(String absolutePath) {
        try {
            return new URI("cloudreve", "my", absolutePath, null).toASCIIString();
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

    private static String recoverableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
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
        if (code == 404 || code == 40044) {
            return failure(CloudreveApiException.Kind.NOT_FOUND, "Cloudreve object was not found", null);
        }
        if (CONFLICT_CODES.contains(code)) {
            return failure(CloudreveApiException.Kind.CONFLICT, "Cloudreve object conflicts with existing state", null);
        }
        if (code == 429 || code >= 500 && code < 600 || code >= 50_000 && code < 60_000) {
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

    private static long effectiveChunkSize(CloudreveUploadSession session, long byteSize) {
        if (session.chunkSize() != 0) return session.chunkSize();
        if (session.relay() || "local".equals(session.policyType()) || "remote".equals(session.policyType())) {
            return Math.max(1L, byteSize);
        }
        throw malformed();
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

    /** Exposes exactly one declared upload part without closing or buffering the caller-owned stream. */
    private static final class ExactLengthInputStream extends InputStream {
        private final InputStream source;
        private long remaining;
        private boolean endedEarly;

        private ExactLengthInputStream(InputStream source, long length) {
            this.source = source;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = source.read();
            if (value < 0) {
                endedEarly = true;
                return -1;
            }
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) return 0;
            if (remaining == 0) return -1;
            int requested = (int) Math.min(length, remaining);
            int read = source.read(bytes, offset, requested);
            if (read < 0) {
                endedEarly = true;
                return -1;
            }
            if (read == 0) {
                int single = read();
                if (single < 0) return -1;
                bytes[offset] = (byte) single;
                return 1;
            }
            remaining -= read;
            return read;
        }

        @Override
        public void close() {
            // The caller owns the source stream lifecycle.
        }

        private boolean finished() {
            return remaining == 0 && !endedEarly;
        }

        private boolean endedEarly() {
            return endedEarly;
        }
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

    /** A back-pressured response stream with an absolute post-header deadline and hard byte ceiling. */
    private static final class DeadlineBoundedBodySubscriber implements HttpResponse.BodySubscriber<InputStream> {
        private final long limit;
        private final long timeoutNanos;
        private final CompletableFuture<InputStream> body = new CompletableFuture<>();
        private final ArrayDeque<byte[]> pending = new ArrayDeque<>();
        private Flow.Subscription subscription;
        private long deadlineNanos;
        private long received;
        private boolean complete;
        private boolean closed;
        private CloudreveApiException streamFailure;

        private DeadlineBoundedBodySubscriber(long limit, Duration timeout) {
            this.limit = limit;
            this.timeoutNanos = timeout.toNanos();
        }

        @Override
        public java.util.concurrent.CompletionStage<InputStream> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription candidate) {
            synchronized (this) {
                if (subscription != null) {
                    candidate.cancel();
                    return;
                }
                subscription = Objects.requireNonNull(candidate);
                deadlineNanos = System.nanoTime() + timeoutNanos;
                body.complete(new SubscriptionInputStream(this));
            }
            CompletableFuture.delayedExecutor(timeoutNanos, TimeUnit.NANOSECONDS).execute(this::expire);
            candidate.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            int batchSize = 0;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > Integer.MAX_VALUE - batchSize) {
                    fail(malformed());
                    return;
                }
                batchSize += buffer.remaining();
            }
            byte[] batch = new byte[batchSize];
            int offset = 0;
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                buffer.get(batch, offset, length);
                offset += length;
            }
            boolean oversized;
            synchronized (this) {
                if (closed || complete || streamFailure != null) return;
                oversized = batchSize > limit - received;
                if (!oversized) {
                    received += batchSize;
                    if (batchSize > 0) pending.addLast(batch);
                    notifyAll();
                }
            }
            if (oversized) {
                fail(malformed());
            } else if (batchSize == 0) {
                requestNext();
            }
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            if (closed || complete || streamFailure != null) return;
            streamFailure = failure(CloudreveApiException.Kind.TRANSIENT,
                    "Cloudreve content request failed", throwable);
            notifyAll();
        }

        @Override
        public synchronized void onComplete() {
            if (closed || streamFailure != null) return;
            complete = true;
            notifyAll();
        }

        private void expire() {
            fail(failure(CloudreveApiException.Kind.TRANSIENT,
                    "Cloudreve content request timed out", null));
        }

        private void fail(CloudreveApiException failure) {
            Flow.Subscription current;
            synchronized (this) {
                if (closed || complete || streamFailure != null) return;
                streamFailure = failure;
                pending.clear();
                current = subscription;
                notifyAll();
            }
            if (current != null) current.cancel();
        }

        private synchronized byte[] awaitNext() {
            while (true) {
                if (streamFailure != null) throw streamFailure;
                if (!pending.isEmpty()) return pending.removeFirst();
                if (complete) return null;
                if (closed) throw failure(CloudreveApiException.Kind.PROVIDER_FAILURE,
                        "Cloudreve content stream is closed", null);
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    expire();
                    continue;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    fail(failure(CloudreveApiException.Kind.TRANSIENT,
                            "Cloudreve content read was interrupted", interrupted));
                }
            }
        }

        private synchronized void throwIfFailed() {
            if (streamFailure != null) throw streamFailure;
            if (!complete && !closed && System.nanoTime() >= deadlineNanos) expire();
            if (streamFailure != null) throw streamFailure;
        }

        private void requestNext() {
            Flow.Subscription current;
            synchronized (this) {
                if (closed || complete || streamFailure != null) return;
                current = subscription;
            }
            if (current != null) current.request(1);
        }

        private void closeStream() {
            Flow.Subscription current;
            synchronized (this) {
                if (closed) return;
                closed = true;
                pending.clear();
                current = subscription;
                notifyAll();
            }
            if (current != null) current.cancel();
        }
    }

    private static final class SubscriptionInputStream extends InputStream {
        private final DeadlineBoundedBodySubscriber subscriber;
        private byte[] current;
        private int offset;
        private boolean closed;

        private SubscriptionInputStream(DeadlineBoundedBodySubscriber subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = read(single, 0, 1);
            return read < 0 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int targetOffset, int length) throws IOException {
            Objects.checkFromIndexSize(targetOffset, length, bytes.length);
            if (closed) throw new IOException("Stream closed");
            if (length == 0) return 0;
            subscriber.throwIfFailed();
            if (current == null || offset == current.length) {
                if (current != null) subscriber.requestNext();
                current = subscriber.awaitNext();
                offset = 0;
                if (current == null) return -1;
            }
            int count = Math.min(length, current.length - offset);
            System.arraycopy(current, offset, bytes, targetOffset, count);
            offset += count;
            return count;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            subscriber.closeStream();
        }
    }
}
