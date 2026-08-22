package com.blog.repository;

import com.blog.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 收藏数据访问接口
 */
@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * 查询用户的收藏记录
     */
    Optional<Favorite> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, String targetType);

    /**
     * 统计某目标的收藏数
     */
    long countByTargetIdAndTargetType(Long targetId, String targetType);

    /**
     * 查询用户某类型的所有收藏（按时间倒序）
     */
    List<Favorite> findByUserIdAndTargetTypeOrderByCreatedAtDesc(Long userId, String targetType);
}
