package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 分页查询文章（关联查询作者名）
     */
    @Select("SELECT a.*, u.username AS author_name FROM article a LEFT JOIN `user` u ON a.user_id = u.id ORDER BY a.create_time DESC")
    Page<Article> selectPageWithAuthor(Page<Article> page);

    /**
     * 按 ID 查询文章（关联查询作者名）
     */
    @Select("SELECT a.*, u.username AS author_name FROM article a LEFT JOIN `user` u ON a.user_id = u.id WHERE a.id = #{id}")
    Article selectByIdWithAuthor(Long id);
}
