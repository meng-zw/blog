package com.blog.repository;

import com.blog.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评论数据访问接口
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 根据目标ID和目标类型查询评论，按创建时间倒序排列
     * @param targetId 目标ID（文章ID或工具ID）
     * @param targetType 目标类型（article/tool）
     * @return 评论列表
     */
    List<Comment> findByTargetIdAndTargetTypeOrderByCreatedAtDesc(Long targetId, String targetType);
}