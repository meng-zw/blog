package com.blog.media;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUploadPlanRateLimiterTest {

    @Test
    void limitsEachAdministratorAndIpPairWithinTheConfiguredWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        MediaProperties properties = new MediaProperties();
        properties.getUploadPlanRateLimit().setMaximumRequests(2);
        properties.getUploadPlanRateLimit().setWindow(Duration.ofMinutes(1));
        MediaUploadPlanRateLimiter limiter = new MediaUploadPlanRateLimiter(properties, clock);

        assertThat(limiter.tryAcquire("owner", "203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("owner", "203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("owner", "203.0.113.10")).isFalse();
        assertThat(limiter.tryAcquire("owner", "203.0.113.11")).isTrue();
        assertThat(limiter.tryAcquire("other", "203.0.113.10")).isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("owner", "203.0.113.10")).isTrue();
    }

    @Test
    void keepsTheInMemoryKeySetBoundedUnderUntrustedIpChurn() {
        MediaProperties properties = new MediaProperties();
        properties.getUploadPlanRateLimit().setMaximumEntries(2);
        MediaUploadPlanRateLimiter limiter = new MediaUploadPlanRateLimiter(properties,
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneId.of("UTC")));

        assertThat(limiter.tryAcquire("owner", "203.0.113.1")).isTrue();
        assertThat(limiter.tryAcquire("owner", "203.0.113.2")).isTrue();
        assertThat(limiter.tryAcquire("owner", "203.0.113.3")).isTrue();
        assertThat(limiter.trackedKeyCount()).isEqualTo(2);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
