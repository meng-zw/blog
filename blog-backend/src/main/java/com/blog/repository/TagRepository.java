package com.blog.repository;

import com.blog.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * 统计每个标签关联的已发布文章数（标签云用）
     * @return 每行 [标签ID, 标签名, 文章数]，按文章数倒序
     */
    @Query(value = "SELECT t.id, t.name, COUNT(at.article_id) FROM tag t " +
            "LEFT JOIN article_tag at ON t.id = at.tag_id " +
            "LEFT JOIN article a ON at.article_id = a.id AND (a.status = 'published' OR a.status IS NULL) " +
            "GROUP BY t.id, t.name ORDER BY COUNT(at.article_id) DESC", nativeQuery = true)
    List<Object[]> countArticlesByTag();
}
