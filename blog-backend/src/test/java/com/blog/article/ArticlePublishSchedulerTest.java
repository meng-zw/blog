package com.blog.article;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ArticlePublishSchedulerTest {
    @Test
    void scheduledTickPublishesContentDueAtTheCurrentInstant() {
        ArticleService service = mock(ArticleService.class);
        Instant now = Instant.parse("2026-08-22T10:00:00Z");
        ArticlePublishScheduler scheduler = new ArticlePublishScheduler(
                service, Clock.fixed(now, ZoneOffset.UTC));

        scheduler.publishDue();

        verify(service).publishDue(now);
    }
}
