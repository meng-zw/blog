package com.blog.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface SiteProfileRepository extends JpaRepository<SiteProfile, Long> {
    @EntityGraph(attributePaths = "avatarMedia")
    Optional<SiteProfile> findFirstByOrderByIdAsc();
}
