package com.blog.media.storage.cloudreve;

import com.blog.support.MySqlIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CloudreveConnectionLockingIntegrationTest extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired CloudreveConnectionRepository connections;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void migrateFreshDatabase() {
        Flyway flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        connections.saveAndFlush(new CloudreveConnection());
    }

    @Test
    void singletonRefreshClaimUsesADatabaseRowLock() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                connections.findSingletonForUpdate().orElseThrow();
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertThat(firstLocked.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> transactions.executeWithoutResult(status -> {
                connections.findSingletonForUpdate().orElseThrow();
                secondLocked.countDown();
            }));

            assertThat(secondLocked.await(150, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertThat(secondLocked.getCount()).isZero();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for test release");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
