package com.blog.media.storage.cloudreve;

/** Indicates that the administrator must establish a fresh Cloudreve authorization. */
public class CloudreveAuthorizationRequiredException extends RuntimeException {
    public CloudreveAuthorizationRequiredException() {
        super("Cloudreve authorization is required");
    }
}
