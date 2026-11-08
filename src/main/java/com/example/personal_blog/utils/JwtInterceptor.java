package com.example.personal_blog.utils;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        if ("OPTIONS".equals(request.getMethod()) || !(handler instanceof HandlerMethod method)
                || "/error".equals(request.getServletPath())) {
            return true;
        }
        PublicEndpoint endpoint = method.getMethodAnnotation(PublicEndpoint.class);
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
        UserContext.setUserId(jwtUtil.getUserId(header.substring(7).trim()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
