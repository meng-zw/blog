package com.blog.media.storage.cloudreve;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudreveOAuthTransactionRepositoryTest {

    @Test
    void doesNotConsumeSessionBoundStateFromAnotherAdministratorSession() {
        CloudreveOAuthTransactionRepository transactions = new CloudreveOAuthTransactionRepository();
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        transactions.save(new CloudreveOAuthTransaction("state", "verifier", 7L, now.plusSeconds(60), 3L, "session-a"), now);

        assertThatThrownBy(() -> transactions.consume("state", 7L, "session-b", now))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);

        transactions.consume("state", 7L, "session-a", now);
    }
}
