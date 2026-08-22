package com.blog.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteProfileRepository extends JpaRepository<SiteProfile, Long> {
    Optional<SiteProfile> findFirstByOrderByIdAsc();
}
