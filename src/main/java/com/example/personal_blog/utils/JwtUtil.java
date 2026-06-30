package com.example.personal_blog.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JwtUtil {

    private static final String SECRET = "blog_secret_key_123456";

    // 生成token
    public static String generateToken(Long userId, String username) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withExpiresAt(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24小时
                .sign(Algorithm.HMAC256(SECRET));
    }

    // 验证token
    public static DecodedJWT verify(String token) {
        return JWT.require(Algorithm.HMAC256(SECRET))
                .build()
                .verify(token);
    }

    // 解析userId
    public static Long getUserId(String token) {
        return verify(token).getClaim("userId").asLong();
    }
}
