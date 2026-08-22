package com.blog.article;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleRepositoryVisibilityContractTest {

    @Test
    void publicTopicFilterRequiresTheTopicItselfToBePublished() throws Exception {
        Query query = ArticleRepository.class.getMethod("findPublicPage", ContentType.class, String.class,
                        String.class, String.class, String.class, Instant.class, Pageable.class)
                .getAnnotation(Query.class);

        assertThat(query.value()).contains(
                "topic.status = com.blog.topic.TopicStatus.PUBLISHED");
    }
}
