package com.blog.identity;

import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class LoginAttemptService {
    private static final int MAX_KEYS = 10_000;
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<LoginKey, FailureWindow> failures = new LinkedHashMap<>();

    public LoginAttemptService() { this(Clock.systemUTC()); }
    LoginAttemptService(Clock clock) { this.clock = clock; }

    public synchronized boolean isBlocked(String username, String clientIp) {
        Instant now = clock.instant();
        removeExpired(now);
        FailureWindow failure = failures.get(key(username, clientIp));
        return failure != null && failure.count() >= MAX_FAILURES;
    }

    public synchronized void recordFailure(String username, String clientIp) {
        Instant now = clock.instant();
        removeExpired(now);
        LoginKey key = key(username, clientIp);
        FailureWindow current = failures.get(key);
        if (current != null) {
            failures.put(key, new FailureWindow(current.firstFailure(), current.count() + 1));
            return;
        }
        if (failures.size() >= MAX_KEYS) {
            failures.remove(failures.entrySet().iterator().next().getKey());
        }
        failures.put(key, new FailureWindow(now, 1));
    }

    public synchronized void recordSuccess(String username, String clientIp) {
        failures.remove(key(username, clientIp));
    }

    private void removeExpired(Instant now) {
        failures.entrySet().removeIf(entry -> !entry.getValue().firstFailure().plus(WINDOW).isAfter(now));
    }

    private static LoginKey key(String username, String clientIp) {
        String normalizedUsername = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        String normalizedIp = clientIp == null ? "" : clientIp.strip();
        return new LoginKey(normalizedUsername, normalizedIp);
    }

    private record LoginKey(String username, String clientIp) {}
    private record FailureWindow(Instant firstFailure, int count) {}
}
