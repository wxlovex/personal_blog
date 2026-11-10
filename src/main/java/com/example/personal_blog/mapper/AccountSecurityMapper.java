package com.example.personal_blog.mapper;

import com.example.personal_blog.dto.StorageUsage;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface AccountSecurityMapper {
    record AccountToken(String tokenHash, Long userId, String purpose, String email, LocalDateTime expiresAt) {}

    @Select("SELECT email FROM verified_email WHERE user_id = #{userId}")
    String verifiedEmail(Long userId);

    @Select("SELECT email FROM verified_email WHERE user_id = #{userId} FOR UPDATE")
    String lockVerifiedEmail(Long userId);

    @Select("SELECT user_id FROM verified_email WHERE email = #{email}")
    Long emailOwner(String email);

    @Delete("DELETE FROM verified_email WHERE user_id = #{userId}")
    void removeEmail(Long userId);

    @Insert("INSERT INTO verified_email (user_id,email,verified_at) VALUES (#{userId},#{email},#{now})")
    void bindEmail(@Param("userId") Long userId, @Param("email") String email, @Param("now") LocalDateTime now);

    @Insert("INSERT INTO account_token (token_hash,user_id,purpose,email,expires_at) VALUES (#{hash},#{userId},#{purpose},#{email},#{expiresAt})")
    void createToken(@Param("hash") String hash, @Param("userId") Long userId, @Param("purpose") String purpose,
                     @Param("email") String email, @Param("expiresAt") LocalDateTime expiresAt);

    @Select("SELECT token_hash,user_id,purpose,email,expires_at FROM account_token WHERE token_hash = #{hash} AND purpose = #{purpose} AND expires_at > #{now}")
    AccountToken token(@Param("hash") String hash, @Param("purpose") String purpose, @Param("now") LocalDateTime now);

    @Select("SELECT token_hash,user_id,purpose,email,expires_at FROM account_token WHERE token_hash = #{hash} AND purpose = #{purpose} AND expires_at > #{now} FOR UPDATE")
    AccountToken lockToken(@Param("hash") String hash, @Param("purpose") String purpose, @Param("now") LocalDateTime now);

    @Delete("DELETE FROM account_token WHERE user_id = #{userId} AND purpose = #{purpose}")
    void removePurpose(@Param("userId") Long userId, @Param("purpose") String purpose);

    @Delete("DELETE FROM account_token WHERE user_id = #{userId}")
    void removeTokens(Long userId);

    @Select("""
            SELECT SUM(t.used_bytes) AS used_bytes, SUM(t.articles) AS articles,
                   SUM(t.comments) AS comments, SUM(t.categories) AS categories
            FROM (
              SELECT COALESCE(SUM(OCTET_LENGTH(title) + OCTET_LENGTH(content)), 0) AS used_bytes,
                     COUNT(*) AS articles, 0 AS comments, 0 AS categories FROM article WHERE user_id = #{userId}
              UNION ALL
              SELECT COALESCE(SUM(OCTET_LENGTH(content)), 0), 0, COUNT(*), 0 FROM comment WHERE user_id = #{userId}
              UNION ALL
              SELECT COALESCE(SUM(OCTET_LENGTH(name) + OCTET_LENGTH(description)), 0), 0, 0, COUNT(*) FROM category WHERE user_id = #{userId}
            ) t
            """)
    StorageUsage storage(Long userId);
}
