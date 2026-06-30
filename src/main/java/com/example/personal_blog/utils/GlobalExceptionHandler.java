package com.example.personal_blog.utils;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException e) {
        log.error("RuntimeException: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(e.getMessage());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<String> handleTokenExpired(TokenExpiredException e) {
        log.warn("Token expired: {}", e.getMessage());
        return ResponseEntity.status(401).body("token已过期，请重新登录");
    }

    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<String> handleSignatureInvalid(SignatureVerificationException e) {
        log.warn("Token签名无效: {}", e.getMessage());
        return ResponseEntity.status(401).body("token签名无效，可能被篡改或复制不完整");
    }

    @ExceptionHandler(JWTVerificationException.class)
    public ResponseEntity<String> handleJwt(JWTVerificationException e) {
        log.warn("JWT验证失败: {}", e.getMessage());
        return ResponseEntity.status(401).body("token无效: " + e.getMessage());
    }
}
