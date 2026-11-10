package com.example.personal_blog.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import com.auth0.jwt.interfaces.DecodedJWT;

@Component
public class JwtUtil {
    private final Algorithm algorithm;
    private final long ttlSeconds;

    public JwtUtil(@Value("${blog.jwt.secret}") String secret,
                   @Value("${blog.jwt.ttl-seconds:86400}") long ttlSeconds) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET 必须设置为至少 32 字节的随机密钥");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("JWT_TTL_SECONDS 必须大于 0");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.ttlSeconds = ttlSeconds;
    }

    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        return JWT.create().withIssuer("personal-blog")
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("userId", userId).withClaim("username", username)
                .withIssuedAt(now).withExpiresAt(now.plusSeconds(ttlSeconds)).sign(algorithm);
    }

    public Long getUserId(String token) {
        return verify(token).userId();
    }

    public record Identity(Long userId, String sessionId, Instant expiresAt) {}

    public Identity verify(String token) {
        try {
            DecodedJWT decoded = JWT.require(algorithm).withIssuer("personal-blog").build().verify(token);
            Long id = decoded.getClaim("userId").asLong();
            String sessionId = decoded.getId();
            if (id == null || id <= 0 || sessionId == null || sessionId.length() != 36
                    || decoded.getExpiresAtAsInstant() == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "登录凭证无效或已过期");
            }
            return new Identity(id, sessionId, decoded.getExpiresAtAsInstant());
        } catch (JWTVerificationException | IllegalArgumentException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录凭证无效或已过期");
        }
    }
}
