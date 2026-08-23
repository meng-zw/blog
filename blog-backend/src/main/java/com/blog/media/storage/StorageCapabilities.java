package com.blog.media.storage;

/**
 * Storage features that determine how clients transfer and read media.
 */
public record StorageCapabilities(boolean directUpload, boolean publicRead) {
}
