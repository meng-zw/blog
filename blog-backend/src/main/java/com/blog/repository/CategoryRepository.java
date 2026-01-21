package com.blog.repository;

import com.blog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 分类数据访问接口
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}