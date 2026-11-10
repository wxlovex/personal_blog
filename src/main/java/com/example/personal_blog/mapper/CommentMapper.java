package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.entity.Comment;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    @Select("""
            SELECT c.*, u.username AS author_name FROM comment c
            LEFT JOIN `user` u ON c.user_id = u.id WHERE c.article_id = #{articleId} AND c.user_id = #{userId}
            ORDER BY c.create_time ASC, c.id ASC
            """)
    Page<Comment> listWithAuthor(Page<Comment> page, @Param("articleId") Long articleId, @Param("userId") Long userId);
}
