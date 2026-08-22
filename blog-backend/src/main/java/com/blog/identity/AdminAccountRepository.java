package com.blog.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByUsernameAndEnabledTrue(String username);
    long countByEnabledTrue();
}
