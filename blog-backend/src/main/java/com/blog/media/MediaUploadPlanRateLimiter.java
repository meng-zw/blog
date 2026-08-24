package com.blog.media;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded first-node upload-plan limiter. Counts are intentionally node-local;
 * multi-node deployments must replace this component with a shared implementation.
 */
@Component
public class MediaUploadPlanRateLimiter {
    private final Clock clock;
    private final int maximumRequests;
    private final Duration window;
    private final int maximumEntries;
    private final Map<RateLimitKey, RequestWindow> requests = new LinkedHashMap<>();

    public MediaUploadPlanRateLimiter(MediaProperties properties) {
        this(properties, Clock.systemUTC());
    }

    MediaUploadPlanRateLimiter(MediaProperties properties, Clock clock) {
        MediaProperties.UploadPlanRateLimit configuration = properties.getUploadPlanRateLimit();
        if (configuration.getMaximumRequests() < 1 || configuration.getMaximumEntries() < 1
                || configuration.getWindow() == null || configuration.getWindow().isZero()
                || configuration.getWindow().isNegative()) {
            throw new IllegalArgumentException("Upload-plan rate-limit values must be positive");
        }
        this.clock = clock;
        this.maximumRequests = configuration.getMaximumRequests();
        this.window = configuration.getWindow();
        this.maximumEntries = configuration.getMaximumEntries();
    }

    public synchronized boolean tryAcquire(String administrator, String clientIp) {
        Instant now = clock.instant();
        removeExpired(now);
        RateLimitKey key = key(administrator, clientIp);
        RequestWindow current = requests.get(key);
        if (current != null && current.count() >= maximumRequests) {
            return false;
        }
        if (current != null) {
            requests.put(key, new RequestWindow(current.startedAt(), current.count() + 1));
            return true;
        }
        while (requests.size() >= maximumEntries) {
            Iterator<RateLimitKey> iterator = requests.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        requests.put(key, new RequestWindow(now, 1));
        return true;
    }

    synchronized int trackedKeyCount() {
        return requests.size();
    }

    private void removeExpired(Instant now) {
        requests.entrySet().removeIf(entry -> !entry.getValue().startedAt().plus(window).isAfter(now));
    }

    private static RateLimitKey key(String administrator, String clientIp) {
        String normalizedAdministrator = administrator == null ? "" : administrator.strip().toLowerCase(Locale.ROOT);
        String normalizedIp = clientIp == null ? "" : clientIp.strip();
        return new RateLimitKey(normalizedAdministrator, normalizedIp);
    }

    private record RateLimitKey(String administrator, String clientIp) {}
    private record RequestWindow(Instant startedAt, int count) {}
}
