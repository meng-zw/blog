package com.blog.media.storage.cloudreve;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/** Owns administrator OAuth state, encrypted token persistence and single-flight refresh. */
@Service
public class CloudreveTokenService {
    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(10);
    private static final Duration ACCESS_EXPIRY_SKEW = Duration.ofMinutes(1);
    private static final long REFRESH_POLL_NANOS = Duration.ofMillis(5).toNanos();
    private static final Duration MINIMUM_REFRESH_CLAIM_LEASE = Duration.ofSeconds(30);
    private static final int MAX_REFRESH_ATTEMPTS_PER_REQUEST = 2;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CloudreveProperties properties;
    private final CloudreveConnectionRepository connections;
    private final CloudreveOAuthTransactionRepository authorizationTransactions;
    private final CloudreveOAuthClient oauth;
    private final CloudreveTokenCipher cipher;
    private final TransactionOperations database;
    private final Clock clock;

    @Autowired
    public CloudreveTokenService(CloudreveProperties properties,
                                 CloudreveConnectionRepository connections,
                                 CloudreveOAuthTransactionRepository authorizationTransactions,
                                 CloudreveOAuthClient oauth,
                                 PlatformTransactionManager transactionManager) {
        this(properties, connections, authorizationTransactions, oauth,
                cipherIfConfigured(properties), new TransactionTemplate(transactionManager), Clock.systemUTC());
    }

    CloudreveTokenService(CloudreveProperties properties,
                          CloudreveConnectionRepository connections,
                          CloudreveOAuthTransactionRepository authorizationTransactions,
                          CloudreveOAuthClient oauth,
                          CloudreveTokenCipher cipher,
                          TransactionOperations database,
                          Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.connections = Objects.requireNonNull(connections);
        this.authorizationTransactions = Objects.requireNonNull(authorizationTransactions);
        this.oauth = Objects.requireNonNull(oauth);
        this.cipher = cipher;
        this.database = Objects.requireNonNull(database);
        this.clock = Objects.requireNonNull(clock);
    }

    public URI beginAuthorization(long adminId) {
        return beginAuthorization(adminId, null);
    }

    public URI beginAuthorization(long adminId, String sessionId) {
        requireAdmin(adminId);
        if (sessionId != null && sessionId.isBlank()) throw new IllegalArgumentException("Administrator session is required");
        Instant now = clock.instant();
        long generation = database.execute(status -> {
            CloudreveConnection connection = connections.findSingletonForUpdate().orElseGet(() -> {
                CloudreveConnection created = new CloudreveConnection();
                return connections.saveAndFlush(created);
            });
            long next = nextGeneration(connection.getAuthorizationGeneration());
            connection.setAuthorizationGeneration(next);
            connections.saveAndFlush(connection);
            return next;
        });
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(64);
        authorizationTransactions.save(new CloudreveOAuthTransaction(
                state, verifier, adminId, now.plus(AUTHORIZATION_TTL), generation, sessionId), now);
        return oauth.authorizationUri(state, verifier);
    }

    public void completeAuthorization(String code, String state, long adminId) {
        completeAuthorization(code, state, adminId, null);
    }

    public void completeAuthorization(String code, String state, long adminId, String sessionId) {
        requireAdmin(adminId);
        CloudreveOAuthTransaction transaction = authorizationTransactions.consume(state, adminId, sessionId, clock.instant());
        if (code == null || code.isBlank()) throw new CloudreveAuthorizationRequiredException();

        // Both calls are deliberately outside the database transaction.
        CloudreveOAuthClient.TokenPair pair = oauth.exchangeCode(code, transaction.codeVerifier());
        CloudreveOAuthClient.UserInfo user = oauth.userInfo(pair.accessToken());
        database.executeWithoutResult(status -> {
            CloudreveConnection connection = connections.findSingletonForUpdate().orElseGet(() -> {
                CloudreveConnection created = new CloudreveConnection();
                return connections.saveAndFlush(created);
            });
            if (connection.getAuthorizationGeneration() != transaction.authorizationGeneration()) {
                throw new CloudreveAuthorizationRequiredException();
            }
            persistPair(connection, pair);
            connection.setAuthorizedSubject(user.subject());
            connection.setAuthorizedDisplayName(user.displayName());
            connection.setStatus(CloudreveConnectionStatus.CONNECTED);
            clearRefreshClaim(connection);
            connections.saveAndFlush(connection);
        });
    }

    public String validAccessToken() {
        ensureCipher();
        long deadline = System.nanoTime() + properties.getRequestTimeout().toNanos();
        int refreshAttempts = 0;
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw refreshUnavailable();
            AccessDecision decision = database.execute(status -> decideAccess());
            if (decision instanceof Ready ready) return ready.accessToken();
            if (decision instanceof RefreshClaim claim) {
                if (refreshAttempts >= MAX_REFRESH_ATTEMPTS_PER_REQUEST) {
                    releaseRefreshClaim(claim.claimToken());
                    throw refreshUnavailable();
                }
                refreshAttempts++;
                remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    releaseRefreshClaim(claim.claimToken());
                    throw refreshUnavailable();
                }
                AccessDecision afterRefresh = performRefresh(claim, Duration.ofNanos(remaining));
                if (afterRefresh instanceof Ready ready) return ready.accessToken();
                if (refreshAttempts >= MAX_REFRESH_ATTEMPTS_PER_REQUEST) throw refreshUnavailable();
                decision = afterRefresh;
            }
            if (decision instanceof Waiting) {
                remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw refreshUnavailable();
                LockSupport.parkNanos(Math.min(REFRESH_POLL_NANOS, remaining));
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    throw new CloudreveOAuthClient.OAuthUnavailableException("Cloudreve token wait was interrupted");
                }
            }
        }
    }

    /**
     * Returns a token after an authenticated API request rejected the supplied token.
     * The stored token is invalidated only when it is still the rejected value, so a
     * concurrent refresh cannot be overwritten or needlessly repeated.
     */
    public String validAccessTokenAfterRejection(String rejectedAccessToken) {
        ensureCipher();
        if (rejectedAccessToken == null || rejectedAccessToken.isBlank()) {
            throw new IllegalArgumentException("Rejected Cloudreve access token is required");
        }
        database.executeWithoutResult(status -> {
            CloudreveConnection connection = connections.findSingletonForUpdate()
                    .orElseThrow(CloudreveAuthorizationRequiredException::new);
            if (connection.getStatus() == CloudreveConnectionStatus.DISCONNECTED
                    || connection.getStatus() == CloudreveConnectionStatus.REAUTH_REQUIRED) {
                throw new CloudreveAuthorizationRequiredException();
            }
            if (connection.getStatus() != CloudreveConnectionStatus.CONNECTED) return;
            try {
                if (sameToken(decrypt(connection, "access"), rejectedAccessToken)) {
                    connection.setAccessTokenExpiresAt(Instant.EPOCH);
                    connections.saveAndFlush(connection);
                }
            } catch (CloudreveTokenCipher.TokenDecryptionException exception) {
                requireReauthorization(connection);
                throw new CloudreveAuthorizationRequiredException();
            }
        });
        return validAccessToken();
    }

    public void disconnect(long adminId) {
        requireAdmin(adminId);
        authorizationTransactions.removeForAdmin(adminId);
        database.executeWithoutResult(status -> connections.findSingletonForUpdate().ifPresent(connection -> {
            connection.setAuthorizationGeneration(nextGeneration(connection.getAuthorizationGeneration()));
            clearTokens(connection);
            clearRefreshClaim(connection);
            connection.setAuthorizedSubject(null);
            connection.setAuthorizedDisplayName(null);
            connection.setGrantedScopes(null);
            connection.setStatus(CloudreveConnectionStatus.DISCONNECTED);
            connections.saveAndFlush(connection);
        }));
    }

    private AccessDecision decideAccess() {
        CloudreveConnection connection = connections.findSingletonForUpdate()
                .orElseThrow(CloudreveAuthorizationRequiredException::new);
        if (connection.getStatus() == CloudreveConnectionStatus.DISCONNECTED
                || connection.getStatus() == CloudreveConnectionStatus.REAUTH_REQUIRED) {
            throw new CloudreveAuthorizationRequiredException();
        }
        Instant now = clock.instant();
        if (connection.getStatus() == CloudreveConnectionStatus.REFRESHING) {
            if (!isStaleRefreshClaim(connection, now)) return Waiting.INSTANCE;
            connection.setStatus(CloudreveConnectionStatus.CONNECTED);
            clearRefreshClaim(connection);
            connections.saveAndFlush(connection);
        }
        try {
            if (hasFreshAccessToken(connection, now)) {
                return new Ready(decrypt(connection, "access"));
            }
            if (connection.getRefreshTokenExpiresAt() == null
                    || !connection.getRefreshTokenExpiresAt().isAfter(now)) {
                requireReauthorization(connection);
                throw new CloudreveAuthorizationRequiredException();
            }
            String refreshToken = decrypt(connection, "refresh");
            List<String> scopes = parseStoredScopes(connection.getGrantedScopes());
            String claimToken = randomUrlToken(24);
            connection.setStatus(CloudreveConnectionStatus.REFRESHING);
            connection.setRefreshClaimToken(claimToken);
            connection.setRefreshClaimedAt(now);
            connections.saveAndFlush(connection);
            return new RefreshClaim(claimToken, refreshToken, scopes);
        } catch (CloudreveTokenCipher.TokenDecryptionException exception) {
            requireReauthorization(connection);
            throw new CloudreveAuthorizationRequiredException();
        }
    }

    private AccessDecision performRefresh(RefreshClaim claim, Duration operationTimeout) {
        CloudreveOAuthClient.TokenPair pair;
        try {
            pair = oauth.refresh(claim.refreshToken(), claim.scopes(), operationTimeout);
        } catch (CloudreveOAuthClient.InvalidGrantException exception) {
            database.executeWithoutResult(status -> updateClaim(claim.claimToken(), this::requireReauthorization));
            throw new CloudreveAuthorizationRequiredException();
        } catch (CloudreveOAuthClient.OAuthUnavailableException | CloudreveOAuthClient.OAuthProtocolException exception) {
            releaseRefreshClaim(claim.claimToken());
            throw exception;
        } catch (RuntimeException exception) {
            releaseRefreshClaim(claim.claimToken());
            throw new CloudreveOAuthClient.OAuthUnavailableException("Cloudreve token refresh failed");
        }
        return database.execute(status -> {
            CloudreveConnection connection = connections.findSingletonForUpdate()
                    .orElseThrow(CloudreveAuthorizationRequiredException::new);
            if (connection.getStatus() == CloudreveConnectionStatus.REFRESHING
                    && claim.claimToken().equals(connection.getRefreshClaimToken())) {
                persistPair(connection, pair);
                connection.setStatus(CloudreveConnectionStatus.CONNECTED);
                clearRefreshClaim(connection);
                connections.saveAndFlush(connection);
                return new Ready(pair.accessToken());
            }
            if (connection.getStatus() == CloudreveConnectionStatus.REFRESHING) {
                return Waiting.INSTANCE;
            }
            if (connection.getStatus() == CloudreveConnectionStatus.CONNECTED) {
                if (hasFreshAccessToken(connection, clock.instant())) {
                    return new Ready(decrypt(connection, "access"));
                }
                return Retry.INSTANCE;
            }
            throw new CloudreveAuthorizationRequiredException();
        });
    }

    private static boolean hasFreshAccessToken(CloudreveConnection connection, Instant now) {
        return connection.getAccessTokenExpiresAt() != null
                && connection.getAccessTokenExpiresAt().isAfter(now.plus(ACCESS_EXPIRY_SKEW));
    }

    private static CloudreveOAuthClient.OAuthUnavailableException refreshUnavailable() {
        return new CloudreveOAuthClient.OAuthUnavailableException("Cloudreve token refresh is still in progress");
    }

    private void releaseRefreshClaim(String claimToken) {
        database.executeWithoutResult(status -> updateClaim(claimToken, connection -> {
            connection.setStatus(CloudreveConnectionStatus.CONNECTED);
            clearRefreshClaim(connection);
            connections.saveAndFlush(connection);
        }));
    }

    private void updateClaim(String claimToken, java.util.function.Consumer<CloudreveConnection> update) {
        connections.findSingletonForUpdate().ifPresent(connection -> {
            if (connection.getStatus() == CloudreveConnectionStatus.REFRESHING
                    && claimToken.equals(connection.getRefreshClaimToken())) {
                update.accept(connection);
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleRefreshClaimOnStartup() {
        // Disabled installations do not initialize or query Cloudreve persistence at startup.
        if (cipher == null) return;
        database.executeWithoutResult(status -> connections.findSingletonForUpdate().ifPresent(connection -> {
            if (connection.getStatus() == CloudreveConnectionStatus.REFRESHING
                    && isStaleRefreshClaim(connection, clock.instant())) {
                connection.setStatus(CloudreveConnectionStatus.CONNECTED);
                clearRefreshClaim(connection);
                connections.saveAndFlush(connection);
            }
        }));
    }

    private void persistPair(CloudreveConnection connection, CloudreveOAuthClient.TokenPair pair) {
        ensureCipher();
        if (connection.getId() == null) throw new IllegalStateException("Cloudreve connection must be persisted before encryption");
        CloudreveTokenCipher.EncryptedToken access = cipher.encrypt(connection.getId(), "access", pair.accessToken());
        CloudreveTokenCipher.EncryptedToken refresh = cipher.encrypt(connection.getId(), "refresh", pair.refreshToken());
        connection.setAccessTokenNonce(access.nonce());
        connection.setAccessTokenCiphertext(access.ciphertext());
        connection.setAccessTokenExpiresAt(pair.accessExpiresAt());
        connection.setRefreshTokenNonce(refresh.nonce());
        connection.setRefreshTokenCiphertext(refresh.ciphertext());
        connection.setRefreshTokenExpiresAt(pair.refreshExpiresAt());
        connection.setGrantedScopes(pair.scopes().stream().distinct().sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.joining(" ")));
    }

    private String decrypt(CloudreveConnection connection, String type) {
        byte[] nonce = "access".equals(type) ? connection.getAccessTokenNonce() : connection.getRefreshTokenNonce();
        byte[] ciphertext = "access".equals(type)
                ? connection.getAccessTokenCiphertext() : connection.getRefreshTokenCiphertext();
        if (nonce == null || ciphertext == null || connection.getId() == null) {
            throw new CloudreveTokenCipher.TokenDecryptionException();
        }
        return cipher.decrypt(connection.getId(), type, new CloudreveTokenCipher.EncryptedToken(nonce, ciphertext));
    }

    private void requireReauthorization(CloudreveConnection connection) {
        clearTokens(connection);
        clearRefreshClaim(connection);
        connection.setStatus(CloudreveConnectionStatus.REAUTH_REQUIRED);
        connections.saveAndFlush(connection);
    }

    private static void clearTokens(CloudreveConnection connection) {
        connection.setAccessTokenCiphertext(null);
        connection.setAccessTokenNonce(null);
        connection.setAccessTokenExpiresAt(null);
        connection.setRefreshTokenCiphertext(null);
        connection.setRefreshTokenNonce(null);
        connection.setRefreshTokenExpiresAt(null);
    }

    private static void clearRefreshClaim(CloudreveConnection connection) {
        connection.setRefreshClaimToken(null);
        connection.setRefreshClaimedAt(null);
    }

    private boolean isStaleRefreshClaim(CloudreveConnection connection, Instant now) {
        Instant claimedAt = connection.getRefreshClaimedAt();
        String token = connection.getRefreshClaimToken();
        return token == null || token.isBlank() || claimedAt == null
                || !now.isBefore(claimedAt.plus(refreshClaimLease()));
    }

    private Duration refreshClaimLease() {
        Duration requestLease = properties.getRequestTimeout().multipliedBy(2);
        return requestLease.compareTo(MINIMUM_REFRESH_CLAIM_LEASE) > 0
                ? requestLease : MINIMUM_REFRESH_CLAIM_LEASE;
    }

    private static long nextGeneration(long current) {
        if (current == Long.MAX_VALUE) throw new IllegalStateException("Cloudreve authorization generation exhausted");
        return current + 1;
    }

    private static List<String> parseStoredScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) throw new CloudreveAuthorizationRequiredException();
        return Arrays.stream(scopes.trim().split("\\s+")).filter(value -> !value.isBlank()).distinct().toList();
    }

    private void ensureCipher() {
        if (cipher == null) throw new CloudreveAuthorizationRequiredException();
    }

    private static CloudreveTokenCipher cipherIfConfigured(CloudreveProperties properties) {
        String key = properties.getTokenEncryptionKey();
        return key == null || key.isBlank() ? null : new CloudreveTokenCipher(key);
    }

    private static String randomUrlToken(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static boolean sameToken(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireAdmin(long adminId) {
        if (adminId <= 0) throw new IllegalArgumentException("Administrator ID is required");
    }

    private sealed interface AccessDecision permits Ready, RefreshClaim, Waiting, Retry {}
    private record Ready(String accessToken) implements AccessDecision {}
    private record RefreshClaim(String claimToken, String refreshToken, List<String> scopes) implements AccessDecision {}
    private enum Waiting implements AccessDecision { INSTANCE }
    private enum Retry implements AccessDecision { INSTANCE }
}
