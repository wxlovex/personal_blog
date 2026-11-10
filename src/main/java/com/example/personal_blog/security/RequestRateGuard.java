package com.example.personal_blog.security;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.controller.UserController;
import com.example.personal_blog.controller.AccountSecurityController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import java.time.Duration;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RequestRateGuard {
    private final RateLimiter limiter;
    private final SecurityProperties properties;

    public void check(HttpServletRequest request, HandlerMethod handler) {
        if (!AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RestController.class)) return;
        // Socket peer only. Forwarded headers must not let clients choose their own rate-limit key.
        String peer = request.getRemoteAddr();
        if (handler.getBeanType() == AccountSecurityController.class && !"GET".equals(request.getMethod())) {
            limiter.acquire("account-" + handler.getMethod().getName() + ":" + peer,
                    properties.getSensitivePerMinute(), Duration.ofMinutes(1));
            return;
        }
        // Revocation has its own budget so journal writes cannot prevent logout.
        if (handler.getBeanType() == UserController.class && handler.getMethod().getName().equals("logout")) {
            limiter.acquire("logout-ip:" + peer, properties.getReadsPerMinute(), Duration.ofMinutes(1));
            return;
        }
        boolean write = Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod());
        limiter.acquire((write ? "write-ip:" : "read-ip:") + peer,
                write ? properties.getWritesPerMinute() : properties.getReadsPerMinute(), Duration.ofMinutes(1));
        // Classify the actual handler, so aliases, encoded paths and matrix parameters share limits.
        if (handler.getBeanType() == UserController.class) {
            if (handler.getMethod().getName().equals("login")) {
                limiter.acquire("login-ip:" + peer, properties.getLoginPerMinute(), Duration.ofMinutes(1));
            } else if (handler.getMethod().getName().equals("register")) {
                if (!properties.isRegistrationEnabled()) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "暂未开放注册");
                }
                limiter.acquire("register-ip:" + peer, properties.getRegistrationPerHour(), Duration.ofHours(1));
            }
        }
    }

    public void checkAuthenticated(HttpServletRequest request, HandlerMethod handler, Long userId) {
        if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())
                && handler.getBeanType() != AccountSecurityController.class
                && !(handler.getBeanType() == UserController.class && handler.getMethod().getName().equals("logout"))) {
            limiter.acquire("write-user:" + userId, properties.getWritesPerMinute(), Duration.ofMinutes(1));
        }
    }
}
