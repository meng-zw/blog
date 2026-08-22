package com.blog.service.impl;

import com.blog.entity.Article;
import com.blog.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 文章定时发布任务：每分钟检查一次到期的定时发布文章并转为已发布
 */
@Component
public class ArticlePublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(ArticlePublishScheduler.class);

    @Autowired
    private ArticleRepository articleRepository;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void publishScheduledArticles() {
        List<Article> dueArticles = articleRepository.findByStatusAndPublishTimeLessThanEqual("scheduled", new Date());
        for (Article article : dueArticles) {
            article.setStatus("published");
            articleRepository.save(article);
            log.info("定时发布文章成功: id={}, title={}", article.getId(), article.getTitle());
        }
    }
}
