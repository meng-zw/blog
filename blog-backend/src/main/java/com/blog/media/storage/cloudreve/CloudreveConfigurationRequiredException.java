package com.blog.media.storage.cloudreve;

/** Indicates Cloudreve authorization was requested while the integration is inactive. */
public class CloudreveConfigurationRequiredException extends RuntimeException {
    public CloudreveConfigurationRequiredException() {
        super("Cloudreve configuration is required");
    }
}
