package com.blog.media.storage.cloudreve;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** Validated Cloudreve v4 upload-session fields used by the streaming upload state machine. */
public record CloudreveUploadSession(
        String id,
        long chunkSize,
        Instant expiresAt,
        String policyId,
        String policyType,
        boolean relay,
        List<URI> uploadUrls,
        String credential,
        URI completionUrl,
        String callbackSecret,
        String fileUri) {

    public CloudreveUploadSession {
        uploadUrls = uploadUrls == null ? List.of() : List.copyOf(uploadUrls);
    }
}
