package com.blog.media.storage;

/** Typed provider failure so application workflows never confuse outages with invalid content. */
public final class ObjectStorageException extends RuntimeException {
    public enum Kind { TRANSIENT, NOT_FOUND }

    private final Kind kind;

    private ObjectStorageException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static ObjectStorageException transientFailure(String message, Throwable cause) {
        return new ObjectStorageException(Kind.TRANSIENT, message, cause);
    }

    public static ObjectStorageException notFound(String message, Throwable cause) {
        return new ObjectStorageException(Kind.NOT_FOUND, message, cause);
    }
}
