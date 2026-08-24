package com.blog.media;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaReferenceCheckerTest {

    @Test
    void recognizesToolMarkdownReferencesInSingleAndBulkChecks() {
        EntityManager entityManager = mock(EntityManager.class);
        List<String> queries = new ArrayList<>();
        when(entityManager.createQuery(any(String.class), eq(Long.class))).thenAnswer(invocation -> {
            String queryText = invocation.getArgument(0);
            queries.add(queryText);
            @SuppressWarnings("unchecked")
            TypedQuery<Long> query = mock(TypedQuery.class);
            when(query.setParameter(any(String.class), any())).thenReturn(query);
            when(query.getSingleResult()).thenReturn(queryText.contains("ToolMedia") ? 1L : 0L);
            when(query.getResultList()).thenReturn(queryText.contains("ToolMedia") ? List.of(9L) : List.of());
            return query;
        });
        MediaReferenceChecker checker = new MediaReferenceChecker(entityManager);

        assertThat(checker.isReferenced(9L)).isTrue();
        assertThat(checker.referencedIds(List.of(8L, 9L))).containsExactly(9L);
        assertThat(queries).anyMatch(query -> query.contains("ToolMedia"));
    }
}
