package com.blog.topic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "topic_article")
@IdClass(TopicArticle.Id.class)
public class TopicArticle {
    @jakarta.persistence.Id
    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @jakarta.persistence.Id
    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Long getTopicId() { return topicId; }
    public void setTopicId(Long topicId) { this.topicId = topicId; }
    public Long getArticleId() { return articleId; }
    public void setArticleId(Long articleId) { this.articleId = articleId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public static class Id implements Serializable {
        private Long topicId;
        private Long articleId;

        public Id() {
        }

        public Id(Long topicId, Long articleId) {
            this.topicId = topicId;
            this.articleId = articleId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Id id)) return false;
            return Objects.equals(topicId, id.topicId) && Objects.equals(articleId, id.articleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topicId, articleId);
        }
    }
}
