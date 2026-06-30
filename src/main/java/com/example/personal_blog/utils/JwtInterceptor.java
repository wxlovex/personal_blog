package com.example.personal_blog.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器
 */
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        String rawHeader = request.getHeader("Authorization");
        log.info("收到Authorization头 (长度={}): {}", rawHeader != null ? rawHeader.length() : 0, rawHeader);

        if (rawHeader == null || !rawHeader.startsWith("Bearer ")) {
            throw new RuntimeException("未登录");
        }

        String token = rawHeader.substring(7).trim();
        log.info("提取token: 长度={}, 前20字符={}, 后10字符={}",
                token.length(),
                token.substring(0, Math.min(20, token.length())),
                token.substring(Math.max(0, token.length() - 10)));

        // 检查常见问题: token被引号包裹
        if (token.startsWith("\"") && token.endsWith("\"")) {
            log.warn("token被双引号包裹，自动去除引号");
            token = token.substring(1, token.length() - 1);
        }

        // 检查token是否包含非法字符(换行/空格)
        if (token.contains("\n") || token.contains(" ") || token.contains("\r")) {
            log.warn("token包含换行符或空格，可能导致签名验证失败!");
        }

        Long userId = JwtUtil.getUserId(token);

        // 👇 存入上下文
        UserContext.setUserId(userId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
