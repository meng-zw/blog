ALTER TABLE topic_article
    ADD CONSTRAINT uk_topic_article_single_article UNIQUE (article_id);
