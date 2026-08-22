package com.blog.topic;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

import static org.assertj.core.api.Assertions.assertThat;

class TopicArticleRepositoryContractTest {

    @Test
    void bulkDeletesFlushAndClearThePersistenceContext() throws Exception {
        assertFlushAndClear("deleteByTopicId");
        assertFlushAndClear("deleteByArticleId");
    }

    private static void assertFlushAndClear(String methodName) throws Exception {
        Modifying annotation = TopicArticleRepository.class.getMethod(methodName, long.class)
                .getAnnotation(Modifying.class);
        assertThat(annotation.flushAutomatically()).isTrue();
        assertThat(annotation.clearAutomatically()).isTrue();
    }
}
