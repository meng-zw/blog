package com.blog.tool;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRepositoryVisibilityContractTest {
    @Test
    void publicQueriesGateOnPublishedStateTimeAndAllDocumentedFilters() throws Exception {
        Query query = ToolRepository.class.getMethod("findPublicPage", String.class, String.class, String.class,
                        Instant.class, Pageable.class)
                .getAnnotation(Query.class);

        assertThat(query.value()).contains(
                "tool.status = com.blog.tool.ToolStatus.PUBLISHED",
                "tool.publishedAt <= :now",
                "category.slug = :categorySlug",
                "tag.slug = :tagSlug",
                "lower(tool.name) like lower(concat('%', :keyword, '%'))",
                "lower(tool.summary) like lower(concat('%', :keyword, '%'))");
    }
}
