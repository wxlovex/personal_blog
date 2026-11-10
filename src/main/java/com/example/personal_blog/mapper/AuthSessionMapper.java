package com.example.personal_blog.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuthSessionMapper {
    @Insert("INSERT INTO auth_session (session_id, user_id, expires_at, created_at) VALUES (#{sessionId}, #{userId}, #{expiresAt}, #{createdAt})")
    void insert(@Param("sessionId") String sessionId, @Param("userId") Long userId,
                @Param("expiresAt") LocalDateTime expiresAt, @Param("createdAt") LocalDateTime createdAt);

    @Select("SELECT COUNT(*) FROM auth_session WHERE session_id = #{sessionId} AND user_id = #{userId} AND expires_at > #{now}")
    int active(@Param("sessionId") String sessionId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Delete("DELETE FROM auth_session WHERE session_id = #{sessionId} AND user_id = #{userId}")
    void revoke(@Param("sessionId") String sessionId, @Param("userId") Long userId);

    @Delete("DELETE FROM auth_session WHERE user_id = #{userId} AND expires_at <= #{now}")
    void deleteExpired(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM auth_session WHERE user_id = #{userId}")
    int countForUser(Long userId);

    @Delete("DELETE FROM auth_session WHERE user_id = #{userId}")
    void revokeAll(Long userId);

    @Select("SELECT COUNT(*) FROM auth_session WHERE user_id = #{userId} AND expires_at > #{now}")
    int activeForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT session_id FROM auth_session WHERE user_id = #{userId} ORDER BY created_at, session_id LIMIT 1")
    String oldestForUser(Long userId);

    @Select("SELECT session_id FROM auth_session WHERE user_id = #{userId} ORDER BY created_at, session_id FOR UPDATE")
    List<String> lockSessions(Long userId);
}
