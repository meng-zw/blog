package com.blog.repository;

import com.blog.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 标签数据访问接口
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 按创建时间倒序查询所有标签
     */
    List<Tag> findAllByOrderByCreatedAtDesc();

    /**
     * 检查标签名称是否存在
     * @param name 标签名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据名称查询标签
     * @param name 标签名称
     * @return 标签实体
     */
    Tag findByName(String name);
}
