package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.RegisterDTO;
import com.example.personal_blog.dto.UserProfile;
import com.example.personal_blog.entity.User;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.service.UserService;
import com.example.personal_blog.security.AuthSessionService;
import com.example.personal_blog.security.RateLimiter;
import com.example.personal_blog.security.SecurityProperties;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Locale;
import org.springframework.util.DigestUtils;
import org.springframework.transaction.annotation.Transactional;
import com.example.personal_blog.mapper.AccountSecurityMapper;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthSessionService sessions;
    private final RateLimiter limiter;
    private final SecurityProperties security;
    private final AccountSecurityMapper accounts;
    // Valid BCrypt hash used for absent accounts, avoiding a fast username-existence branch.
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Override
    public UserProfile register(RegisterDTO dto) {
        if (!security.isRegistrationEnabled()) throw new BusinessException(ResultCode.FORBIDDEN, "暂未开放注册");
        if (dto.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码的UTF-8编码不能超过72字节");
        }
        if (userMapper.selectCount(new QueryWrapper<User>().eq("username", dto.username())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.username());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setEmail(dto.email() == null || dto.email().isBlank() ? null : dto.email());
        user.setCreateTime(LocalDateTime.now(java.time.ZoneOffset.UTC));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        return UserProfile.from(user);
    }

    @Override
    @Transactional
    public String login(String username, String password) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        String key = "login-account:" + (user == null
                ? "missing-" + DigestUtils.md5DigestAsHex(username.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
                : user.getId());
        limiter.acquire(key, security.getAccountLoginPerMinute(), Duration.ofMinutes(1));
        // Verify the latest hash and issue the session under one user lock. A password
        // change must not race with an earlier successful password check.
        if (user != null) user = userMapper.lockById(user.getId());
        boolean valid = passwordEncoder.matches(password.getBytes(StandardCharsets.UTF_8).length > 72
                ? "invalid-overlong-password" : password, user == null ? DUMMY_HASH : user.getPassword());
        if (user == null || password.getBytes(StandardCharsets.UTF_8).length > 72 || !valid) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        limiter.forget(key);
        return sessions.issue(user.getId(), user.getUsername());
    }

    @Override
    public UserProfile me() {
        User user = userMapper.selectById(UserContext.requireUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        return UserProfile.from(user, accounts.verifiedEmail(user.getId()) != null);
    }
}
