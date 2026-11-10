package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.personal_blog.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    @Select("""
            SELECT c.*, (SELECT COUNT(*) FROM article a WHERE a.category_id = c.id AND a.user_id = #{userId}) AS article_count
            FROM category c WHERE c.user_id = #{userId} ORDER BY c.name ASC, c.id ASC
            """)
    List<Category> listWithCounts(@Param("userId") Long userId);

    @Select("SELECT * FROM category WHERE id = #{id} AND user_id = #{userId} FOR UPDATE")
    Category lockById(@Param("id") Long id, @Param("userId") Long userId);
}
