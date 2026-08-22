package com.blog.taxonomy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SlugAllocationLockRepository extends JpaRepository<SlugAllocationLock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from SlugAllocationLock lock where lock.id = 1")
    SlugAllocationLock acquire();
}
