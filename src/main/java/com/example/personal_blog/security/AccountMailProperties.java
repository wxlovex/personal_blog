package com.example.personal_blog.security;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.util.Set;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "blog.mail")
public class AccountMailProperties {
    private boolean enabled = false;
    @NotBlank @Email private String from = "noreply@example.invalid";
    @NotBlank private String publicBaseUrl = "http://localhost:8080";
    @Value("${spring.mail.host:localhost}") private String smtpHost;
    @Value("${spring.mail.properties.mail.smtp.starttls.required:true}") private boolean tlsRequired;
    @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") private boolean sslEnabled;

    @PostConstruct
    public void validateTransport() {
        if (sslEnabled && tlsRequired) {
            throw new IllegalArgumentException("SMTP SSL 与必需的 STARTTLS 不能同时启用，请设置 MAIL_STARTTLS_REQUIRED=false");
        }
        URI base = URI.create(publicBaseUrl);
        boolean local = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(base.getHost() == null ? "" : base.getHost());
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !("https".equals(base.getScheme()) || (local && "http".equals(base.getScheme())))
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
            throw new IllegalArgumentException("MAIL_PUBLIC_BASE_URL 必须是可信HTTPS站点根地址（本机允许HTTP）");
        }
        if (enabled && !Set.of("localhost", "127.0.0.1", "::1").contains(smtpHost) && !tlsRequired && !sslEnabled) {
            throw new IllegalArgumentException("非本机SMTP必须要求STARTTLS或SSL");
        }
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }
}
