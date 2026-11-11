package com.example.personal_blog.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import com.example.personal_blog.security.RequestProtectionFilter;
import com.example.personal_blog.security.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.net.URI;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final JwtInterceptor interceptor;
    private final String[] origins;

    public WebConfig(JwtInterceptor interceptor,
                     @Value("${blog.cors.allowed-origins:}") String allowedOrigins) {
        this.interceptor = interceptor;
        this.origins = parseOrigins(allowedOrigins);
    }

    static String[] parseOrigins(String raw) {
        return Arrays.stream(raw.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).map(origin -> {
                    URI uri;
                    try {
                        uri = URI.create(origin);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("CORS 来源必须是有效的 HTTP 或 HTTPS 站点根地址");
                    }
                    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                            || uri.getHost() == null || uri.getHost().isEmpty()
                            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                            || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                            || uri.getPort() > 65535) {
                        throw new IllegalArgumentException("CORS 来源必须是 HTTP 或 HTTPS 站点根地址，不能包含通配符、凭证、路径、查询或片段");
                    }
                    return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
                }).toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RequestProtectionFilter> requestProtection(SecurityProperties properties, ObjectMapper json) {
        FilterRegistrationBean<RequestProtectionFilter> bean = new FilterRegistrationBean<>(new RequestProtectionFilter(properties, json));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
