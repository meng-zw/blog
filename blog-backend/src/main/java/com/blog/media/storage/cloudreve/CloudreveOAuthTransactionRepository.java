package com.blog.media.storage.cloudreve;

import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Atomic in-process store for short-lived, one-use OAuth transactions. */
@Repository
public class CloudreveOAuthTransactionRepository {
    private final ConcurrentMap<String, CloudreveOAuthTransaction> transactions = new ConcurrentHashMap<>();

    public synchronized void save(CloudreveOAuthTransaction transaction) {
        Objects.requireNonNull(transaction, "OAuth transaction is required");
        long newestGeneration = transactions.values().stream()
                .filter(existing -> existing.adminId() == transaction.adminId())
                .mapToLong(CloudreveOAuthTransaction::authorizationGeneration)
                .max().orElse(Long.MIN_VALUE);
        if (newestGeneration > transaction.authorizationGeneration()) return;
        removeForAdminLocked(transaction.adminId());
        transactions.put(key(transaction.state()), transaction);
    }

    public synchronized void save(CloudreveOAuthTransaction transaction, Instant now) {
        Objects.requireNonNull(now, "Current time is required");
        purgeExpired(now);
        save(transaction);
    }

    public synchronized CloudreveOAuthTransaction consume(String state, long adminId, Instant now) {
        if (state == null || state.isBlank() || adminId <= 0) throw new CloudreveAuthorizationRequiredException();
        Objects.requireNonNull(now, "Current time is required");
        purgeExpired(now);
        String key = key(state);
        CloudreveOAuthTransaction transaction = transactions.get(key);
        boolean expired = transaction != null && !now.isBefore(transaction.expiresAt());
        if (transaction == null || transaction.adminId() != adminId || expired) {
            if (expired) transactions.remove(key, transaction);
            throw new CloudreveAuthorizationRequiredException();
        }
        if (!transactions.remove(key, transaction)) throw new CloudreveAuthorizationRequiredException();
        return transaction;
    }

    public synchronized void removeForAdmin(long adminId) {
        removeForAdminLocked(adminId);
    }

    private void removeForAdminLocked(long adminId) {
        transactions.entrySet().removeIf(entry -> entry.getValue().adminId() == adminId);
    }

    private void purgeExpired(Instant now) {
        transactions.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private static String key(String state) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
