package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.personal_blog.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    @Select("""
            SELECT c.*, (SELECT COUNT(*) FROM article a WHERE a.category_id = c.id) AS article_count
            FROM category c ORDER BY c.name ASC, c.id ASC
            """)
    List<Category> listWithCounts();

    @Select("SELECT * FROM category WHERE id = #{id} FOR UPDATE")
    Category lockById(Long id);
}
