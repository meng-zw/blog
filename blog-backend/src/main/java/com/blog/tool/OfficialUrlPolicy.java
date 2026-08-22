package com.blog.tool;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;

/** Central URL normalization and policy used by both request validation and persistence. */
public final class OfficialUrlPolicy {
    private OfficialUrlPolicy() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Official URL is required");
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Official URL is required");
        }
        if (normalized.length() > 1000 || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Official URL is invalid");
        }
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque() || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null || uri.getRawAuthority() == null) {
                throw new IllegalArgumentException("Official URL must be an absolute HTTPS URL without user info");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Official URL is invalid", exception);
        }
    }
}
