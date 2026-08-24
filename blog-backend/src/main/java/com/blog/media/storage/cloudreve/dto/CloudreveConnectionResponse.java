package com.blog.media.storage.cloudreve.dto;

import com.blog.media.storage.cloudreve.CloudreveConnectionStatus;

import java.time.Instant;
import java.util.List;

/** Administrator-safe projection of the singleton Cloudreve connection. */
public record CloudreveConnectionResponse(boolean configured, CloudreveConnectionStatus status,
                                         String authorizedSubject, String authorizedDisplayName,
                                         List<String> grantedScopes, Instant accessTokenExpiresAt,
                                         Instant refreshTokenExpiresAt, String rootPath) {
}
