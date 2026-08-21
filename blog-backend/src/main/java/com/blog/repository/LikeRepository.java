package com.blog.repository;

import com.blog.entity.LikeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 点赞数据访问接口
 */
@Repository
public interface LikeRepository extends JpaRepository<LikeRecord, Long> {

    /**
     * 检查用户是否已点赞
     * @param userId 用户ID
     * @param targetId 目标ID
     * @param targetType 目标类型
     * @return 点赞记录（存在则返回）
     */
    Optional<LikeRecord> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, String targetType);

    /**
     * 统计文章/工具的点赞数
     * @param targetId 目标ID
     * @param targetType 目标类型
     * @return 点赞数
     */
    long countByTargetIdAndTargetType(Long targetId, String targetType);
}
