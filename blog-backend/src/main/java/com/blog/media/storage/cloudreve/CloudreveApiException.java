package com.blog.media.storage.cloudreve;

/** Provider-neutral classification for Cloudreve file API and content transport failures. */
public final class CloudreveApiException extends RuntimeException {
    public enum Kind { NOT_FOUND, CONFLICT, TRANSIENT, PROVIDER_FAILURE }

    private final Kind kind;
    private final Integer diagnosticCode;

    CloudreveApiException(Kind kind, String message) {
        this(kind, message, null, null);
    }

    CloudreveApiException(Kind kind, String message, Throwable cause) {
        this(kind, message, cause, null);
    }

    CloudreveApiException(Kind kind, String message, Throwable cause, Integer diagnosticCode) {
        super(message, cause);
        this.kind = java.util.Objects.requireNonNull(kind, "Cloudreve failure kind is required");
        this.diagnosticCode = diagnosticCode;
    }

    public Kind kind() {
        return kind;
    }

    Integer diagnosticCode() {
        return diagnosticCode;
    }

    static String diagnosticReason(CloudreveApiException failure) {
        return switch (failure.getMessage()) {
            case "Cloudreve returned an untrusted origin" -> "UNTRUSTED_PROVIDER_ORIGIN";
            case "Cloudreve storage policy did not match the approved S3 policy" -> "POLICY_MISMATCH";
            case "Cloudreve storage policy is unsupported" -> "UNSUPPORTED_POLICY";
            case "Cloudreve returned an invalid response" -> "INVALID_PROVIDER_RESPONSE";
            case "Cloudreve multipart completion was rejected" -> "MULTIPART_COMPLETION_REJECTED";
            case "Cloudreve upload callback was rejected" -> "UPLOAD_CALLBACK_REJECTED";
            case "Cloudreve upload redirect was refused" -> "UPLOAD_REDIRECT_REFUSED";
            case "Cloudreve upload content length did not match" -> "CONTENT_LENGTH_MISMATCH";
            case "Cloudreve upload content exceeded its declared size" -> "CONTENT_LENGTH_EXCEEDED";
            case "Cloudreve request parameters were rejected" -> "REQUEST_PARAMETER_ERROR";
            case "Cloudreve request was rejected" -> "CLOUDREVE_REQUEST_REJECTED";
            default -> failure.kind().name();
        };
    }
}
