package com.blog.media.storage.cloudreve;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CloudreveConnectionRepository extends JpaRepository<CloudreveConnection, Long> {
    Optional<CloudreveConnection> findBySingletonKey(byte singletonKey);

    default Optional<CloudreveConnection> findSingleton() {
        return findBySingletonKey(CloudreveConnection.SINGLETON_KEY);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from CloudreveConnection connection where connection.singletonKey = 1")
    Optional<CloudreveConnection> findSingletonForUpdate();
}
