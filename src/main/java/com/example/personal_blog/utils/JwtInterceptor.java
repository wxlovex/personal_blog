package com.example.personal_blog.utils;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.security.AuthSessionService;
import com.example.personal_blog.security.RequestRateGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private final AuthSessionService sessions;
    private final RequestRateGuard rateGuard;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        if ("OPTIONS".equals(request.getMethod()) || !(handler instanceof HandlerMethod method)
                || "/error".equals(request.getServletPath())) {
            return true;
        }
        PublicEndpoint endpoint = method.getMethodAnnotation(PublicEndpoint.class);
        response.setHeader("Cache-Control", "no-store");
        rateGuard.check(request, method);
        // 登录/注册必须能在客户端仍携带旧 Token 时使用。
        if (endpoint != null && endpoint.ignoreToken()) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            if (endpoint != null) {
                return true;
            }
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录凭证格式不正确");
        }
        JwtUtil.Identity identity = sessions.authenticate(header.substring(7).trim());
        rateGuard.checkAuthenticated(request, method, identity.userId());
        UserContext.setUserId(identity.userId());
        UserContext.setSessionId(identity.sessionId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
