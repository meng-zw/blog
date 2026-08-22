package com.blog.tool;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.Locale;

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
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Official URL is invalid");
        }
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque() || uri.getRawAuthority() == null
                    || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Official URL must be an absolute HTTPS URL without user info");
            }
            Authority authority = authority(uri.getRawAuthority());
            StringBuilder canonical = new StringBuilder("https://").append(authority.host());
            if (authority.port() != -1) canonical.append(':').append(authority.port());
            if (uri.getRawPath() != null) canonical.append(uri.getRawPath());
            if (uri.getRawQuery() != null) canonical.append('?').append(uri.getRawQuery());
            if (uri.getRawFragment() != null) canonical.append('#').append(uri.getRawFragment());
            String result = new URI(canonical.toString()).toASCIIString();
            if (result.length() > 1000) {
                throw new IllegalArgumentException("Official URL is too long after canonicalization");
            }
            return result;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Official URL is invalid", exception);
        }
    }

    private static Authority authority(String rawAuthority) {
        if (rawAuthority.isBlank() || rawAuthority.contains("@") || rawAuthority.startsWith("[")
                || rawAuthority.contains("\u3002") || rawAuthority.contains("\uff0e") || rawAuthority.contains("\uff61")) {
            throw new IllegalArgumentException("Official URL host is invalid");
        }
        int colon = rawAuthority.lastIndexOf(':');
        if (colon != -1 && rawAuthority.indexOf(':') != colon) {
            throw new IllegalArgumentException("Official URL host is invalid");
        }
        String rawHost = colon == -1 ? rawAuthority : rawAuthority.substring(0, colon);
        String rawPort = colon == -1 ? null : rawAuthority.substring(colon + 1);
        if (rawHost.isEmpty() || (rawPort != null && (rawPort.isEmpty() || !rawPort.chars().allMatch(Character::isDigit)))) {
            throw new IllegalArgumentException("Official URL port is invalid");
        }
        String host;
        try {
            host = IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Official URL host is invalid", exception);
        }
        if (host.length() > 253 || host.endsWith(".")) {
            throw new IllegalArgumentException("Official URL host is invalid");
        }
        for (String label : host.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                throw new IllegalArgumentException("Official URL host is invalid");
            }
        }
        int port = -1;
        if (rawPort != null) {
            try {
                port = Integer.parseInt(rawPort);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Official URL port is invalid", exception);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Official URL port is invalid");
            }
        }
        return new Authority(host, port);
    }

    private record Authority(String host, int port) {
    }
}
