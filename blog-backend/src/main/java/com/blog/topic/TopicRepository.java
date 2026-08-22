package com.blog.topic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Optional<Topic> findByNameIgnoreCase(String name);
    Optional<Topic> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Topic> findAllByOrderByUpdatedAtDesc();
    List<Topic> findAllByStatusOrderByUpdatedAtDesc(TopicStatus status);
}
