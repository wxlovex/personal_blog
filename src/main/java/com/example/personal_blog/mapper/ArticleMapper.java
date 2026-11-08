package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.entity.Article;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
    String LIST_FIELDS = """
            SELECT a.id, a.user_id, a.category_id, a.title, a.view_count, a.like_count,
                   a.create_time, a.update_time, LEFT(a.content, 200) AS summary,
                   u.username AS author_name, c.name AS category_name,
                   CASE WHEN EXISTS (SELECT 1 FROM article_like al
                       WHERE al.article_id = a.id AND al.user_id = #{viewerId})
                       THEN TRUE ELSE FALSE END AS liked,
                   CASE WHEN EXISTS (SELECT 1 FROM user_subscription us
                       WHERE us.author_id = a.user_id AND us.subscriber_id = #{viewerId})
                       THEN TRUE ELSE FALSE END AS subscribed
            FROM article a LEFT JOIN `user` u ON a.user_id = u.id
            LEFT JOIN category c ON a.category_id = c.id
            """;
    String FILTERS = """
            <where>
                <if test="categoryId != null">a.category_id = #{categoryId}</if>
                <if test="authorId != null">AND a.user_id = #{authorId}</if>
                <if test="keyword != null and keyword != ''">AND LOCATE(#{keyword}, a.title) &gt; 0</if>
                <if test="subscriptionsOnly">AND EXISTS (SELECT 1 FROM user_subscription us
                    WHERE us.author_id = a.user_id AND us.subscriber_id = #{viewerId})</if>
            </where>
            """;

    @Select("<script>" + LIST_FIELDS + FILTERS + " ORDER BY subscribed DESC, a.create_time DESC, a.id DESC</script>")
    Page<Article> selectPageWithAuthor(Page<Article> page, @Param("categoryId") Long categoryId,
                                     @Param("authorId") Long authorId, @Param("keyword") String keyword,
                                     @Param("viewerId") Long viewerId,
                                     @Param("subscriptionsOnly") boolean subscriptionsOnly);

    @Select("""
            SELECT a.*, u.username AS author_name, c.name AS category_name,
                CASE WHEN EXISTS (SELECT 1 FROM article_like al
                    WHERE al.article_id = a.id AND al.user_id = #{viewerId})
                    THEN TRUE ELSE FALSE END AS liked,
                CASE WHEN EXISTS (SELECT 1 FROM user_subscription us
                    WHERE us.author_id = a.user_id AND us.subscriber_id = #{viewerId})
                    THEN TRUE ELSE FALSE END AS subscribed
            FROM article a LEFT JOIN `user` u ON a.user_id = u.id
            LEFT JOIN category c ON a.category_id = c.id WHERE a.id = #{id}
            """)
    Article selectByIdWithAuthor(@Param("id") Long id, @Param("viewerId") Long viewerId);

    @Select("<script>" + LIST_FIELDS + """
            <where><if test="categoryId != null">a.category_id = #{categoryId}</if></where>
            ORDER BY <choose><when test="sort == 'likes'">a.like_count</when>
                <otherwise>a.view_count</otherwise></choose> DESC,
                a.create_time DESC, a.id DESC LIMIT #{limit}
            </script>
            """)
    List<Article> ranking(@Param("sort") String sort, @Param("limit") int limit,
                          @Param("categoryId") Long categoryId, @Param("viewerId") Long viewerId);

    @Select("SELECT * FROM article WHERE id = #{id} FOR UPDATE")
    Article lockById(Long id);

    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViews(Long id);

    @Update("UPDATE article SET like_count = like_count + #{delta} WHERE id = #{id}")
    int changeLikes(@Param("id") Long id, @Param("delta") int delta);
}
