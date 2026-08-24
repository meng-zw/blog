package com.blog.media.storage.cloudreve;

/** Lifecycle states for the singleton administrator Cloudreve OAuth connection. */
public enum CloudreveConnectionStatus {
    DISCONNECTED,
    CONNECTED,
    REFRESHING,
    REAUTH_REQUIRED
}
