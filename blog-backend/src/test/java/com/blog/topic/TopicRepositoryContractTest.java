package com.blog.topic;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class TopicRepositoryContractTest {
    @Test
    void publicPageUsesPublishedDatabaseOrderingAndFetchesCoverInThePageQuery() throws Exception {
        var method = TopicRepository.class.getMethod("findPublishedPage", Pageable.class);
        Query query = method.getAnnotation(Query.class);
        EntityGraph graph = method.getAnnotation(EntityGraph.class);

        assertThat(method.getReturnType()).isEqualTo(Page.class);
        assertThat(query.value()).contains("TopicStatus.PUBLISHED", "topic.sortOrder asc", "topic.id asc");
        assertThat(query.countQuery()).contains("TopicStatus.PUBLISHED");
        assertThat(graph.attributePaths()).containsExactly("coverMedia");
    }
}
