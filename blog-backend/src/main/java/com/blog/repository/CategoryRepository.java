package com.blog.repository;

import com.blog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分类数据访问接口
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 按创建时间倒序查询所有分类
     */
    List<Category> findAllByOrderByCreatedAtDesc();

    /**
     * 根据类型查询分类
     * @param type 分类类型 (article/tool)
     * @return 分类列表
     */
    List<Category> findByType(String type);

    /**
     * 检查分类名称是否存在
     * @param name 分类名称
     * @return 是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据名称查询分类
     * @param name 分类名称
     * @return 分类实体
     */
    Category findByName(String name);
}