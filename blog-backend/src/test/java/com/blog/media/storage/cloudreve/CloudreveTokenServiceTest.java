package com.blog.media.storage.cloudreve;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.net.URI;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudreveTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void authorizationStateIsBoundToAdminExpiresAndCanBeConsumedOnlyOnce() {
        CloudreveOAuthTransactionRepository repository = new CloudreveOAuthTransactionRepository();
        CloudreveOAuthTransaction transaction = new CloudreveOAuthTransaction(
                "state", "verifier", 42L, NOW.plusSeconds(60));
        repository.save(transaction);

        assertThatThrownBy(() -> repository.consume("state", 43L, NOW))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        assertThatThrownBy(() -> repository.consume("state", 42L, NOW.plusSeconds(60)))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);

        repository.save(transaction);
        assertThat(repository.consume("state", 42L, NOW).codeVerifier()).isEqualTo("verifier");
        assertThatThrownBy(() -> repository.consume("state", 42L, NOW))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
    }

    @Test
    void replacingAuthorizationForAnAdminIsAtomicAndPurgesExpiredTransactions() {
        CloudreveOAuthTransactionRepository repository = new CloudreveOAuthTransactionRepository();
        repository.save(new CloudreveOAuthTransaction("expired", "old", 41L, NOW.minusSeconds(1), 1));
        repository.save(new CloudreveOAuthTransaction("first", "one", 42L, NOW.plusSeconds(60), 1));
        repository.save(new CloudreveOAuthTransaction("second", "two", 42L, NOW.plusSeconds(60), 2));

        assertThatThrownBy(() -> repository.consume("expired", 41L, NOW))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        assertThatThrownBy(() -> repository.consume("first", 42L, NOW))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        assertThat(repository.consume("second", 42L, NOW).authorizationGeneration()).isEqualTo(2);
    }

    @Test
    void concurrentAuthorizationReplacementLeavesExactlyOneStateForAnAdmin() throws Exception {
        CloudreveOAuthTransactionRepository repository = new CloudreveOAuthTransactionRepository();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await();
                repository.save(new CloudreveOAuthTransaction("first", "one", 42L, NOW.plusSeconds(60), 1), NOW);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                repository.save(new CloudreveOAuthTransaction("second", "two", 42L, NOW.plusSeconds(60), 2), NOW);
                return null;
            });
            start.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        }

        int consumable = 0;
        for (String state : List.of("first", "second")) {
            try {
                repository.consume(state, 42L, NOW);
                consumable++;
            } catch (CloudreveAuthorizationRequiredException ignored) {
                // The losing state must be gone.
            }
        }
        assertThat(consumable).isOne();
    }

    @Test
    void delayedOlderAuthorizationCannotEvictANewerGeneration() {
        CloudreveOAuthTransactionRepository repository = new CloudreveOAuthTransactionRepository();
        repository.save(new CloudreveOAuthTransaction("newer", "two", 42L, NOW.plusSeconds(60), 2), NOW);
        repository.save(new CloudreveOAuthTransaction("older", "one", 42L, NOW.plusSeconds(60), 1), NOW);

        assertThatThrownBy(() -> repository.consume("older", 42L, NOW))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        assertThat(repository.consume("newer", 42L, NOW).authorizationGeneration()).isEqualTo(2);
    }

    @Test
    void transactionToStringDoesNotRevealStateOrVerifier() {
        CloudreveOAuthTransaction transaction = new CloudreveOAuthTransaction(
                "state-secret", "verifier-secret", 42L, NOW.plusSeconds(60));

        assertThat(transaction.toString()).doesNotContain("state-secret").doesNotContain("verifier-secret");
    }

    @Test
    void failedTokenExchangeStillConsumesStateAndPreventsReplay() {
        Fixture fixture = new Fixture();
        when(fixture.oauth.exchangeCode(anyString(), anyString()))
                .thenThrow(new CloudreveOAuthClient.OAuthUnavailableException("timeout"));
        URI authorization = fixture.service.beginAuthorization(42L);
        String state = queryParameter(authorization, "state");

        assertThatThrownBy(() -> fixture.service.completeAuthorization("code", state, 42L))
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class);
        assertThatThrownBy(() -> fixture.service.completeAuthorization("code", state, 42L))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        verify(fixture.oauth, times(1)).exchangeCode(anyString(), anyString());
    }

    @Test
    void reauthorizationAtomicallyReplacesOldEncryptedCredentialsAndIdentity() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(600));
        fixture.stubSuccessfulAuthorization("new-access", "new-refresh");
        String state = queryParameter(fixture.service.beginAuthorization(42L), "state");

        fixture.service.completeAuthorization("code", state, 42L);

        CloudreveConnection saved = fixture.store.connection.get();
        assertThat(saved.getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);
        assertThat(saved.getAuthorizedSubject()).isEqualTo("subject-2");
        assertThat(saved.getAuthorizedDisplayName()).isEqualTo("New User");
        assertThat(fixture.decrypt(saved, "access")).isEqualTo("new-access");
        assertThat(fixture.decrypt(saved, "refresh")).isEqualTo("new-refresh");
        assertThat(saved.getGrantedScopes()).isEqualTo("Files.Write offline_access openid profile");
    }

    @Test
    void blankCallbackCodeConsumesStateWithoutCallingCloudreve() {
        Fixture fixture = new Fixture();
        String state = queryParameter(fixture.service.beginAuthorization(42L), "state");

        assertThatThrownBy(() -> fixture.service.completeAuthorization(" ", state, 42L))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        assertThatThrownBy(() -> fixture.service.completeAuthorization("code", state, 42L))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        verify(fixture.oauth, times(0)).exchangeCode(anyString(), anyString());
    }

    @Test
    void returnsUnexpiredAccessTokenWithoutNetwork() {
        Fixture fixture = new Fixture();
        fixture.store.connected("access", "refresh", NOW.plusSeconds(600), NOW.plusSeconds(1200));

        assertThat(fixture.service.validAccessToken()).isEqualTo("access");
        verify(fixture.oauth, times(0)).refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class));
    }

    @Test
    void refreshesOutsideTheDatabaseTransactionAndPersistsRotatedPair() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class))).thenAnswer(invocation -> {
            assertThat(fixture.transactions.active.get()).isFalse();
            return fixture.pair("new-access", "rotated-refresh");
        });

        assertThat(fixture.service.validAccessToken()).isEqualTo("new-access");

        CloudreveConnection saved = fixture.store.connection.get();
        assertThat(fixture.decrypt(saved, "refresh")).isEqualTo("rotated-refresh");
        assertThat(saved.getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);
    }

    @Test
    void onlyOneOfTwoConcurrentCallersUsesTheRefreshToken() throws Exception {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class))).thenAnswer(invocation -> {
            refreshStarted.countDown();
            assertThat(releaseRefresh.await(2, TimeUnit.SECONDS)).isTrue();
            return fixture.pair("new-access", "rotated-refresh");
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(fixture.service::validAccessToken);
            assertThat(refreshStarted.await(2, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(fixture.service::validAccessToken);
            releaseRefresh.countDown();

            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("new-access");
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("new-access");
        }
        verify(fixture.oauth, times(1)).refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class));
    }

    @Test
    void expiredLosingOwnerNeverReturnsStoredExpiredAccessAfterReplacementClaimFails() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        Fixture fixture = new Fixture(Duration.ofSeconds(2), clock);
        fixture.store.connected("expired-access", "refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        CountDownLatch ownerAStarted = new CountDownLatch(1);
        CountDownLatch releaseOwnerA = new CountDownLatch(1);
        AtomicInteger refreshCalls = new AtomicInteger();
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class))).thenAnswer(invocation -> {
            return switch (refreshCalls.incrementAndGet()) {
                case 1 -> {
                    ownerAStarted.countDown();
                    assertThat(releaseOwnerA.await(2, TimeUnit.SECONDS)).isTrue();
                    yield fixture.pair("owner-a-unpersisted", "owner-a-refresh");
                }
                case 2 -> throw new CloudreveOAuthClient.OAuthUnavailableException("owner B transient failure");
                case 3 -> fixture.pair("eventual-valid-access", "eventual-valid-refresh");
                default -> throw new AssertionError("unexpected extra refresh attempt");
            };
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var ownerA = executor.submit(fixture.service::validAccessToken);
            assertThat(ownerAStarted.await(2, TimeUnit.SECONDS)).isTrue();
            clock.advance(Duration.ofSeconds(120));

            assertThatThrownBy(fixture.service::validAccessToken)
                    .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class);
            assertThat(fixture.store.connection.get().getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);

            releaseOwnerA.countDown();
            assertThat(ownerA.get(2, TimeUnit.SECONDS)).isEqualTo("eventual-valid-access");
        }

        assertThat(fixture.decrypt(fixture.store.connection.get(), "access")).isEqualTo("eventual-valid-access");
        assertThat(refreshCalls).hasValue(3);
    }

    @Test
    void staleRefreshClaimIsReclaimedAfterAServiceRestart() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        CloudreveConnection connection = fixture.store.connection.get();
        connection.setStatus(CloudreveConnectionStatus.REFRESHING);
        connection.setRefreshClaimToken("dead-process");
        connection.setRefreshClaimedAt(NOW.minusSeconds(120));
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class)))
                .thenReturn(fixture.pair("new-access", "rotated-refresh"));

        CloudreveTokenService restarted = fixture.serviceWithKey(Fixture.KEY);
        assertThat(restarted.validAccessToken()).isEqualTo("new-access");

        assertThat(connection.getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);
        assertThat(connection.getRefreshClaimToken()).isNull();
        assertThat(connection.getRefreshClaimedAt()).isNull();
        verify(fixture.oauth, times(1)).refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class));
    }

    @Test
    void startupRecoveryReleasesAnExpiredDurableRefreshClaimWithoutNetworkIo() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        CloudreveConnection connection = fixture.store.connection.get();
        connection.setStatus(CloudreveConnectionStatus.REFRESHING);
        connection.setRefreshClaimToken("dead-process");
        connection.setRefreshClaimedAt(NOW.minusSeconds(120));

        fixture.service.recoverStaleRefreshClaimOnStartup();

        assertThat(connection.getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);
        assertThat(connection.getRefreshClaimToken()).isNull();
        assertThat(connection.getRefreshClaimedAt()).isNull();
        verify(fixture.oauth, times(0)).refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class));
    }

    @Test
    void freshRefreshClaimWaitsInsteadOfStealingAnotherOwnersToken() {
        Fixture fixture = new Fixture(Duration.ofMillis(40));
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        CloudreveConnection connection = fixture.store.connection.get();
        connection.setStatus(CloudreveConnectionStatus.REFRESHING);
        connection.setRefreshClaimToken("live-process");
        connection.setRefreshClaimedAt(NOW);

        assertThatThrownBy(fixture.service::validAccessToken)
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class);
        assertThat(connection.getRefreshClaimToken()).isEqualTo("live-process");
        verify(fixture.oauth, times(0)).refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class));
    }

    @Test
    void malformedRefreshResponseReleasesOnlyItsClaimAndCanBeRetried() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class)))
                .thenThrow(new CloudreveOAuthClient.OAuthProtocolException("invalid response"))
                .thenReturn(fixture.pair("new-access", "rotated-refresh"));

        assertThatThrownBy(fixture.service::validAccessToken)
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class);
        assertThat(fixture.store.connection.get().getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);
        assertThat(fixture.store.connection.get().getRefreshClaimToken()).isNull();

        assertThat(fixture.service.validAccessToken()).isEqualTo("new-access");
        verify(fixture.oauth, times(2)).refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class));
    }

    @Test
    void staleOwnerCannotReleaseAReplacementRefreshClaim() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class))).thenAnswer(invocation -> {
            CloudreveConnection connection = fixture.store.connection.get();
            connection.setRefreshClaimToken("replacement-owner");
            connection.setRefreshClaimedAt(NOW);
            throw new CloudreveOAuthClient.OAuthProtocolException("invalid response");
        });

        assertThatThrownBy(fixture.service::validAccessToken)
                .isInstanceOf(CloudreveOAuthClient.OAuthProtocolException.class);

        CloudreveConnection saved = fixture.store.connection.get();
        assertThat(saved.getStatus()).isEqualTo(CloudreveConnectionStatus.REFRESHING);
        assertThat(saved.getRefreshClaimToken()).isEqualTo("replacement-owner");
    }

    @Test
    void uncheckedRefreshFailureIsNormalizedAndReleasesItsClaim() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class)))
                .thenThrow(new IllegalStateException("decoder implementation failed"));

        assertThatThrownBy(fixture.service::validAccessToken)
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class)
                .hasMessageNotContaining("decoder implementation failed");

        CloudreveConnection saved = fixture.store.connection.get();
        assertThat(saved.getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);
        assertThat(saved.getRefreshClaimToken()).isNull();
    }

    @Test
    void invalidGrantRequiresReauthorizationAndClearsPersistedTokens() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class)))
                .thenThrow(new CloudreveOAuthClient.InvalidGrantException());

        assertThatThrownBy(fixture.service::validAccessToken)
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        assertThat(fixture.store.connection.get().getStatus()).isEqualTo(CloudreveConnectionStatus.REAUTH_REQUIRED);
        assertThat(fixture.store.connection.get().getAccessTokenCiphertext()).isNull();
        assertThat(fixture.store.connection.get().getRefreshTokenCiphertext()).isNull();
    }

    @Test
    void transientRefreshFailureLeavesConnectionRetryable() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.minusSeconds(1), NOW.plusSeconds(1200));
        when(fixture.oauth.refresh(anyString(), org.mockito.ArgumentMatchers.anyList(), any(Duration.class)))
                .thenThrow(new CloudreveOAuthClient.OAuthUnavailableException("timeout"));

        assertThatThrownBy(fixture.service::validAccessToken)
                .isInstanceOf(CloudreveOAuthClient.OAuthUnavailableException.class);
        assertThat(fixture.store.connection.get().getStatus()).isEqualTo(CloudreveConnectionStatus.CONNECTED);
        assertThat(fixture.decrypt(fixture.store.connection.get(), "refresh")).isEqualTo("old-refresh");
    }

    @Test
    void encryptionKeyMismatchFailsClosedAndRequiresReauthorization() {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.plusSeconds(600), NOW.plusSeconds(1200));
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        CloudreveTokenService wrongKeyService = fixture.serviceWithKey(Base64.getEncoder().encodeToString(otherKey));

        assertThatThrownBy(wrongKeyService::validAccessToken)
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
        assertThat(fixture.store.connection.get().getStatus()).isEqualTo(CloudreveConnectionStatus.REAUTH_REQUIRED);
    }

    @Test
    void disconnectClearsCredentialsAndPendingAuthorization() {
        Fixture fixture = new Fixture();
        fixture.store.connected("access", "refresh", NOW.plusSeconds(600), NOW.plusSeconds(1200));
        String state = queryParameter(fixture.service.beginAuthorization(42L), "state");

        fixture.service.disconnect(42L);

        assertThat(fixture.store.connection.get().getStatus()).isEqualTo(CloudreveConnectionStatus.DISCONNECTED);
        assertThat(fixture.store.connection.get().getAccessTokenCiphertext()).isNull();
        assertThatThrownBy(() -> fixture.transactionsRepository.consume(state, 42L, NOW))
                .isInstanceOf(CloudreveAuthorizationRequiredException.class);
    }

    @Test
    void callbackCannotReconnectAfterDisconnectAdvancesAuthorizationGeneration() throws Exception {
        Fixture fixture = new Fixture();
        fixture.store.connected("old-access", "old-refresh", NOW.plusSeconds(600), NOW.plusSeconds(1200));
        CountDownLatch exchangeStarted = new CountDownLatch(1);
        CountDownLatch releaseExchange = new CountDownLatch(1);
        when(fixture.oauth.exchangeCode(anyString(), anyString())).thenAnswer(invocation -> {
            exchangeStarted.countDown();
            assertThat(releaseExchange.await(2, TimeUnit.SECONDS)).isTrue();
            return fixture.pair("late-access", "late-refresh");
        });
        when(fixture.oauth.userInfo("late-access"))
                .thenReturn(new CloudreveOAuthClient.UserInfo("late", "Late"));
        String state = queryParameter(fixture.service.beginAuthorization(42L), "state");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var callback = executor.submit(() -> fixture.service.completeAuthorization("code", state, 42L));
            assertThat(exchangeStarted.await(2, TimeUnit.SECONDS)).isTrue();
            fixture.service.disconnect(42L);
            releaseExchange.countDown();
            assertThatThrownBy(() -> callback.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(CloudreveAuthorizationRequiredException.class);
        }

        assertThat(fixture.store.connection.get().getStatus()).isEqualTo(CloudreveConnectionStatus.DISCONNECTED);
        assertThat(fixture.store.connection.get().getAccessTokenCiphertext()).isNull();
    }

    @Test
    void olderPausedCallbackCannotOverwriteACompletedNewerAuthorization() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch oldExchangeStarted = new CountDownLatch(1);
        CountDownLatch releaseOldExchange = new CountDownLatch(1);
        when(fixture.oauth.exchangeCode(org.mockito.ArgumentMatchers.eq("old-code"), anyString())).thenAnswer(invocation -> {
            oldExchangeStarted.countDown();
            assertThat(releaseOldExchange.await(2, TimeUnit.SECONDS)).isTrue();
            return fixture.pair("old-access", "old-refresh");
        });
        when(fixture.oauth.exchangeCode(org.mockito.ArgumentMatchers.eq("new-code"), anyString()))
                .thenReturn(fixture.pair("new-access", "new-refresh"));
        when(fixture.oauth.userInfo("old-access")).thenReturn(new CloudreveOAuthClient.UserInfo("old", "Old"));
        when(fixture.oauth.userInfo("new-access")).thenReturn(new CloudreveOAuthClient.UserInfo("new", "New"));
        String oldState = queryParameter(fixture.service.beginAuthorization(42L), "state");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var oldCallback = executor.submit(() -> fixture.service.completeAuthorization("old-code", oldState, 42L));
            assertThat(oldExchangeStarted.await(2, TimeUnit.SECONDS)).isTrue();
            String newState = queryParameter(fixture.service.beginAuthorization(42L), "state");
            fixture.service.completeAuthorization("new-code", newState, 42L);
            releaseOldExchange.countDown();
            assertThatThrownBy(() -> oldCallback.get(2, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(CloudreveAuthorizationRequiredException.class);
        }

        CloudreveConnection saved = fixture.store.connection.get();
        assertThat(saved.getAuthorizedSubject()).isEqualTo("new");
        assertThat(fixture.decrypt(saved, "access")).isEqualTo("new-access");
    }

    private static String queryParameter(URI uri, String name) {
        return java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .filter(pair -> java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8).equals(name))
                .map(pair -> java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
    }

    private static final class Fixture {
        private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
        final CloudreveProperties properties = properties();
        final CloudreveOAuthClient oauth = mock(CloudreveOAuthClient.class);
        final CloudreveOAuthTransactionRepository transactionsRepository = new CloudreveOAuthTransactionRepository();
        final FakeTransactions transactions = new FakeTransactions();
        final FakeConnectionStore store = new FakeConnectionStore(KEY);
        final Clock clock;
        final CloudreveTokenService service;

        Fixture() { this(Duration.ofSeconds(2), Clock.fixed(NOW, ZoneOffset.UTC)); }

        Fixture(Duration requestTimeout) {
            this(requestTimeout, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        Fixture(Duration requestTimeout, Clock clock) {
            this.clock = clock;
            properties.setRequestTimeout(requestTimeout);
            when(oauth.authorizationUri(anyString(), anyString())).thenAnswer(invocation ->
                    URI.create("https://cloud.example/session/authorize?state="
                            + java.net.URLEncoder.encode(invocation.getArgument(0), java.nio.charset.StandardCharsets.UTF_8)));
            service = serviceWithKey(KEY);
        }

        CloudreveTokenService serviceWithKey(String key) {
            return new CloudreveTokenService(properties, store.repository, transactionsRepository, oauth,
                    new CloudreveTokenCipher(key), transactions, clock);
        }

        void stubSuccessfulAuthorization(String access, String refresh) {
            when(oauth.exchangeCode(anyString(), anyString())).thenReturn(pair(access, refresh));
            when(oauth.userInfo(access)).thenReturn(new CloudreveOAuthClient.UserInfo("subject-2", "New User"));
        }

        CloudreveOAuthClient.TokenPair pair(String access, String refresh) {
            return new CloudreveOAuthClient.TokenPair(access, refresh, NOW.plusSeconds(600), NOW.plusSeconds(1200),
                    List.of("openid", "profile", "offline_access", "Files.Write"));
        }

        String decrypt(CloudreveConnection connection, String type) {
            CloudreveTokenCipher cipher = new CloudreveTokenCipher(KEY);
            byte[] nonce = type.equals("access") ? connection.getAccessTokenNonce() : connection.getRefreshTokenNonce();
            byte[] ciphertext = type.equals("access") ? connection.getAccessTokenCiphertext() : connection.getRefreshTokenCiphertext();
            return cipher.decrypt(connection.getId(), type, new CloudreveTokenCipher.EncryptedToken(nonce, ciphertext));
        }

        private static CloudreveProperties properties() {
            CloudreveProperties properties = new CloudreveProperties();
            properties.setBaseUrl(URI.create("https://cloud.example"));
            properties.setAuthorizationUri(URI.create("https://cloud.example/session/authorize"));
            properties.setRedirectUri(URI.create("https://blog.example/oauth/callback"));
            properties.setClientId("client-id");
            properties.setClientSecret("client-secret");
            properties.setRequestTimeout(Duration.ofSeconds(2));
            return properties;
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) { instant = new AtomicReference<>(initial); }
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }

    private static final class FakeConnectionStore {
        final AtomicReference<CloudreveConnection> connection = new AtomicReference<>();
        final CloudreveConnectionRepository repository = mock(CloudreveConnectionRepository.class);
        final CloudreveTokenCipher cipher;

        FakeConnectionStore(String key) {
            cipher = new CloudreveTokenCipher(key);
            when(repository.findSingletonForUpdate()).thenAnswer(invocation -> Optional.ofNullable(connection.get()));
            when(repository.findSingleton()).thenAnswer(invocation -> Optional.ofNullable(connection.get()));
            when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                CloudreveConnection saved = invocation.getArgument(0);
                if (saved.getId() == null) saved.setId(1L);
                setVersion(saved, saved.getVersion() + 1);
                connection.set(saved);
                return saved;
            });
        }

        void connected(String access, String refresh, Instant accessExpiry, Instant refreshExpiry) {
            CloudreveConnection value = new CloudreveConnection();
            value.setId(1L);
            CloudreveTokenCipher.EncryptedToken encryptedAccess = cipher.encrypt(1L, "access", access);
            CloudreveTokenCipher.EncryptedToken encryptedRefresh = cipher.encrypt(1L, "refresh", refresh);
            value.setAccessTokenNonce(encryptedAccess.nonce());
            value.setAccessTokenCiphertext(encryptedAccess.ciphertext());
            value.setRefreshTokenNonce(encryptedRefresh.nonce());
            value.setRefreshTokenCiphertext(encryptedRefresh.ciphertext());
            value.setAccessTokenExpiresAt(accessExpiry);
            value.setRefreshTokenExpiresAt(refreshExpiry);
            value.setGrantedScopes("Files.Write offline_access openid profile");
            value.setStatus(CloudreveConnectionStatus.CONNECTED);
            connection.set(value);
        }

        private static void setVersion(CloudreveConnection connection, long version) {
            try {
                var field = CloudreveConnection.class.getDeclaredField("version");
                field.setAccessible(true);
                field.setLong(connection, version);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static final class FakeTransactions implements TransactionOperations {
        final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        private final Object lock = new Object();

        @Override
        public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            synchronized (lock) {
                active.set(true);
                try {
                    return action.doInTransaction(new SimpleTransactionStatus());
                } finally {
                    active.set(false);
                }
            }
        }

    }
}
