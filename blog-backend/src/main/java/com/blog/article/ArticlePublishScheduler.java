package com.blog.article;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component("secureArticlePublishScheduler")
public class ArticlePublishScheduler {
    private final ArticleService articleService;
    private final Clock clock;

    @Autowired
    public ArticlePublishScheduler(ArticleService articleService) {
        this(articleService, Clock.systemUTC());
    }

    ArticlePublishScheduler(ArticleService articleService, Clock clock) {
        this.articleService = articleService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void publishDue() {
        articleService.publishDue(clock.instant());
    }
}
