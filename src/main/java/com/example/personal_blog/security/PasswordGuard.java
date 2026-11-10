package com.example.personal_blog.security;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class PasswordGuard {
    private final BCryptPasswordEncoder encoder;
    private final RateLimiter limiter;
    private final SecurityProperties security;

    public void verify(User user, String password) {
        String key = "reauth-account:" + user.getId();
        limiter.acquire(key, security.getSensitivePerMinute(), Duration.ofMinutes(1));
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72 || !encoder.matches(password, user.getPassword())) {
            // A failed reauthentication does not invalidate a still-valid login session.
            throw new BusinessException(ResultCode.FORBIDDEN, "当前密码不正确");
        }
        limiter.forget(key);
    }

    public void validateNew(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码至少8个字符，且不超过72个UTF-8字节");
        }
    }
}
