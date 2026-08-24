package com.blog.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolMediaRepository extends JpaRepository<ToolMedia, ToolMediaId> {
    List<ToolMedia> findByTool_Id(Long toolId);

    void deleteByTool_Id(Long toolId);

    boolean existsById_MediaId(Long mediaId);
}
