package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.personal_blog.dto.SubscribedAuthor;
import com.example.personal_blog.entity.UserSubscription;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface UserSubscriptionMapper extends BaseMapper<UserSubscription> {
    // 唯一键冲突时保留原订阅时间；重试不会插入重复关系或改变列表顺序。
    @Insert("""
            INSERT INTO user_subscription (subscriber_id, author_id, create_time)
            VALUES (#{subscriberId}, #{authorId}, #{createTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int subscribe(@Param("subscriberId") Long subscriberId, @Param("authorId") Long authorId,
                  @Param("createTime") LocalDateTime createTime);

    @Delete("DELETE FROM user_subscription WHERE subscriber_id = #{subscriberId} AND author_id = #{authorId}")
    int unsubscribe(@Param("subscriberId") Long subscriberId, @Param("authorId") Long authorId);

    @Select("""
            SELECT u.id, u.username, s.create_time AS subscribed_at,
                   (SELECT COUNT(*) FROM article a WHERE a.user_id = u.id) AS article_count
            FROM user_subscription s JOIN `user` u ON u.id = s.author_id
            WHERE s.subscriber_id = #{subscriberId}
            ORDER BY s.create_time DESC, s.author_id DESC
            """)
    Page<SubscribedAuthor> listAuthors(Page<SubscribedAuthor> page, @Param("subscriberId") Long subscriberId);
}
