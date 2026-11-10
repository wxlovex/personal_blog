package com.example.personal_blog.security;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.StorageUsage;
import com.example.personal_blog.mapper.AccountSecurityMapper;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.mapper.AuthSessionMapper;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AccountQuotaService {
    private final UserMapper users;
    private final AccountSecurityMapper accounts;
    private final SecurityProperties security;
    private final AuthSessionMapper sessions;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUser() {
        if (users.lockId(UserContext.requireUserId()) == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        // A queued write may have passed the interceptor before a password change/revocation.
        if (UserContext.getSessionId() != null && sessions.active(UserContext.getSessionId(),
                UserContext.requireUserId(), LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void check(long bytes, int articles, int comments, int categories) {
        StorageUsage used = accounts.storage(UserContext.requireUserId());
        if ((bytes > 0 && used.getUsedBytes() + bytes > security.getMaxStorageBytes())
                || (articles > 0 && used.getArticles() + articles > security.getMaxArticles())
                || (comments > 0 && used.getComments() + comments > security.getMaxComments())
                || (categories > 0 && used.getCategories() + categories > security.getMaxCategories())) {
            throw new BusinessException(ResultCode.CONFLICT, "已达到账号存储或条目上限，请先删除不需要的内容");
        }
    }

    public void requireVerifiedEmail() {
        if (security.isRequireVerifiedEmailForPublication() && accounts.verifiedEmail(UserContext.requireUserId()) == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "发布广场或修改已公开正文前，请先在账号安全中验证邮箱");
        }
    }

    public static long bytes(String... values) {
        long count = 0;
        for (String value : values) if (value != null) count += value.getBytes(StandardCharsets.UTF_8).length;
        return count;
    }
}
