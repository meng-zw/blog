package com.blog.identity;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    @Test
    void blocksFifthFailureForNormalizedUsernameAndClientIp() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(clock);

        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordFailure(" Owner ", "127.0.0.1");
        }

        assertTrue(service.isBlocked("owner", "127.0.0.1"));
        assertFalse(service.isBlocked("owner", "127.0.0.2"));
    }

    @Test
    void successfulLoginClearsFailures() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordFailure("owner", "127.0.0.1");
        }

        service.recordSuccess("owner", "127.0.0.1");

        assertFalse(service.isBlocked("owner", "127.0.0.1"));
    }

    @Test
    void failuresExpireAfterFifteenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordFailure("owner", "127.0.0.1");
        }

        clock.advance(Duration.ofMinutes(15).plusMillis(1));

        assertFalse(service.isBlocked("owner", "127.0.0.1"));
    }

    @Test
    void evictsOldestKeyWhenTenThousandKeyCapacityIsReached() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordFailure("oldest", "127.0.0.1");
        }
        for (int key = 1; key < 10_000; key++) {
            clock.advance(Duration.ofMillis(1));
            service.recordFailure("user-" + key, "127.0.0.1");
        }

        clock.advance(Duration.ofMillis(1));
        service.recordFailure("newest", "127.0.0.1");

        assertFalse(service.isBlocked("oldest", "127.0.0.1"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
