package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.entity.Article;
import com.example.personal_blog.dto.PlazaArticle;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
    String LIST_FIELDS = """
            SELECT a.id, a.user_id, a.category_id, a.title, a.view_count, a.like_count,
                   a.create_time, a.update_time, a.published, a.plaza_category, a.published_time,
                   LEFT(a.content, 200) AS summary,
                   u.username AS author_name, c.name AS category_name,
                   FALSE AS liked, FALSE AS subscribed
            FROM article a LEFT JOIN `user` u ON a.user_id = u.id
            LEFT JOIN category c ON a.category_id = c.id AND c.user_id = a.user_id
            """;
    String FILTERS = """
            <where>
                a.user_id = #{viewerId}
                <if test="categoryId != null">AND a.category_id = #{categoryId}</if>
                <if test="authorId != null">AND a.user_id = #{authorId}</if>
                <if test="keyword != null and keyword != ''">AND LOCATE(#{keyword}, a.title) &gt; 0</if>
            </where>
            """;

    @Select("<script>" + LIST_FIELDS + FILTERS + " ORDER BY a.create_time DESC, a.id DESC</script>")
    Page<Article> selectPageWithAuthor(Page<Article> page, @Param("categoryId") Long categoryId,
                                     @Param("authorId") Long authorId, @Param("keyword") String keyword,
                                     @Param("viewerId") Long viewerId);

    @Select("""
            SELECT a.*, u.username AS author_name, c.name AS category_name,
                FALSE AS liked, FALSE AS subscribed
            FROM article a LEFT JOIN `user` u ON a.user_id = u.id
            LEFT JOIN category c ON a.category_id = c.id AND c.user_id = a.user_id
            WHERE a.id = #{id} AND a.user_id = #{viewerId}
            """)
    Article selectByIdWithAuthor(@Param("id") Long id, @Param("viewerId") Long viewerId);

    @Select("""
            <script>
            SELECT a.id, a.user_id AS author_id, u.username AS author_name, a.title,
                   LEFT(a.content, 200) AS summary, a.plaza_category AS category, a.published_time, a.update_time
            FROM article a JOIN `user` u ON a.user_id = u.id
            WHERE a.published = TRUE
            <if test="category != null">AND a.plaza_category = #{category}</if>
            <if test="keyword != null and keyword != ''">AND LOCATE(#{keyword}, a.title) &gt; 0</if>
            ORDER BY a.published_time DESC, a.id DESC
            </script>
            """)
    Page<PlazaArticle> selectPlazaPage(Page<PlazaArticle> page, @Param("category") String category,
                                      @Param("keyword") String keyword);

    @Select("""
            SELECT a.id, a.user_id AS author_id, u.username AS author_name, a.title, a.content,
                   a.plaza_category AS category, a.published_time, a.update_time
            FROM article a JOIN `user` u ON a.user_id = u.id
            WHERE a.id = #{id} AND a.published = TRUE
            """)
    PlazaArticle selectPlazaById(Long id);

    @Select("SELECT * FROM article WHERE id = #{id} AND user_id = #{userId} FOR UPDATE")
    Article lockOwnedById(@Param("id") Long id, @Param("userId") Long userId);
}
