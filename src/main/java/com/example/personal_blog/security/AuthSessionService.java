package com.example.personal_blog.security;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.mapper.AuthSessionMapper;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.utils.JwtUtil;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private static final int MAX_SESSIONS = 20;
    private final AuthSessionMapper sessions;
    private final UserMapper users;
    private final JwtUtil jwt;

    @Transactional
    public String issue(Long userId, String username) {
        if (users.lockId(userId) == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        sessions.deleteExpired(userId, LocalDateTime.now(ZoneOffset.UTC));
        // Bound session storage per account, including repeated successful logins.
        var active = sessions.lockSessions(userId);
        while (active.size() >= MAX_SESSIONS) {
            sessions.revoke(active.remove(0), userId);
        }
        String token = jwt.generateToken(userId, username);
        JwtUtil.Identity identity = jwt.verify(token);
        sessions.insert(identity.sessionId(), userId, LocalDateTime.ofInstant(identity.expiresAt(), ZoneOffset.UTC),
                LocalDateTime.now(ZoneOffset.UTC));
        return token;
    }

    public JwtUtil.Identity authenticate(String token) {
        JwtUtil.Identity identity = jwt.verify(token);
        if (sessions.active(identity.sessionId(), identity.userId(), LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return identity;
    }

    @Transactional
    public void logout() {
        Long userId = UserContext.requireUserId();
        users.lockId(userId);
        String sessionId = UserContext.getSessionId();
        if (sessionId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        sessions.revoke(sessionId, userId);
    }
}
