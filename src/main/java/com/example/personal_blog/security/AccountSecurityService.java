package com.example.personal_blog.security;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.AccountRequests.*;
import com.example.personal_blog.dto.AccountSecurityStatus;
import com.example.personal_blog.entity.User;
import com.example.personal_blog.mapper.AccountSecurityMapper;
import com.example.personal_blog.mapper.AuthSessionMapper;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountSecurityService {
    public record AccountChanged(Long userId, String notifyEmail) {}
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String VERIFY = "VERIFY_EMAIL";
    private static final String RESET = "RESET_PASSWORD";
    private final UserMapper users;
    private final AuthSessionMapper sessions;
    private final AccountSecurityMapper accounts;
    private final PasswordGuard passwords;
    private final BCryptPasswordEncoder encoder;
    private final RateLimiter limiter;
    private final SecurityProperties security;
    private final AccountMailProperties mailProperties;
    private final AccountMailService mail;

    public AccountSecurityStatus status() {
        Long id = UserContext.requireUserId();
        String email = accounts.verifiedEmail(id);
        return new AccountSecurityStatus(mailProperties.isEnabled(), email != null, email,
                sessions.activeForUser(id, now()), accounts.storage(id), security.getMaxStorageBytes(),
                security.getMaxArticles(), security.getMaxComments(), security.getMaxCategories(),
                security.isRequireVerifiedEmailForPublication());
    }

    @Transactional
    public String changePassword(ChangePassword request) {
        User user = lockedCurrentUser();
        passwords.verify(user, request.currentPassword());
        passwords.validateNew(request.newPassword());
        if (encoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        replacePassword(user, request.newPassword());
        return accounts.verifiedEmail(user.getId());
    }

    @Transactional
    public void logoutAll(CurrentPassword request) {
        User user = lockedCurrentUser();
        passwords.verify(user, request.currentPassword());
        sessions.revokeAll(user.getId());
        accounts.removeTokens(user.getId());
    }

    @Transactional
    public AccountMailService.Outgoing prepareVerification(VerifyEmailRequest request) {
        mail.requireEnabled();
        User user = lockedCurrentUser();
        passwords.verify(user, request.currentPassword());
        String email = normalizeEmail(request.email());
        // Rejected ownership probes also consume the requester's account budget.
        limiter.acquire("verify-mail-user:" + user.getId(), security.getEmailPerHour(), Duration.ofHours(1));
        Long owner = accounts.emailOwner(email);
        if (owner != null && !owner.equals(user.getId())) {
            throw new BusinessException(ResultCode.CONFLICT, "该邮箱无法绑定，请使用其他邮箱");
        }
        limiter.acquire("verify-mail-target:" + hash(email), security.getEmailPerHour(), Duration.ofHours(1));
        return tokenMail(user.getId(), VERIFY, email, 30, "/account/verify-email", "行间 · 验证邮箱");
    }

    @Transactional
    public AccountChanged confirmEmail(String token) {
        mail.requireEnabled();
        var entry = lockToken(token, VERIFY);
        Long owner = accounts.emailOwner(entry.email());
        if (owner != null && !owner.equals(entry.userId())) {
            throw new BusinessException(ResultCode.CONFLICT, "该邮箱无法绑定，请重新申请");
        }
        String previous = accounts.verifiedEmail(entry.userId());
        accounts.removeEmail(entry.userId());
        accounts.bindEmail(entry.userId(), entry.email(), now());
        User user = users.selectById(entry.userId());
        user.setEmail(entry.email());
        users.updateById(user);
        sessions.revokeAll(entry.userId());
        accounts.removeTokens(entry.userId());
        return new AccountChanged(entry.userId(), previous != null && !previous.equals(entry.email()) ? previous : null);
    }

    @Transactional
    public AccountMailService.Outgoing prepareReset(String address) {
        mail.requireEnabled();
        String email = normalizeEmail(address);
        limiter.acquire("reset-mail-target:" + hash(email), security.getEmailPerHour(), Duration.ofHours(1));
        Long id = accounts.emailOwner(email);
        if (id == null) return null;
        if (users.lockId(id) == null || !email.equals(accounts.lockVerifiedEmail(id))) return null;
        return tokenMail(id, RESET, email, 15, "/reset-password", "行间 · 重置密码");
    }

    @Transactional
    public AccountChanged resetPassword(ResetPassword request) {
        mail.requireEnabled();
        var entry = lockToken(request.token(), RESET);
        // A reset link cannot survive replacement/removal of the verified address.
        if (!entry.email().equals(accounts.verifiedEmail(entry.userId()))) throw invalidLink();
        passwords.validateNew(request.newPassword());
        replacePassword(users.selectById(entry.userId()), request.newPassword());
        return new AccountChanged(entry.userId(), entry.email());
    }

    private void replacePassword(User user, String password) {
        user.setPassword(encoder.encode(password));
        users.updateById(user);
        sessions.revokeAll(user.getId());
        accounts.removeTokens(user.getId());
    }

    private User lockedCurrentUser() {
        Long id = UserContext.requireUserId();
        User user = users.lockById(id);
        if (user == null || sessions.active(UserContext.getSessionId(), id, now()) != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return user;
    }

    private AccountSecurityMapper.AccountToken lockToken(String raw, String purpose) {
        String digest = hash(raw);
        var entry = accounts.token(digest, purpose, now());
        if (entry == null || users.lockId(entry.userId()) == null) throw invalidLink();
        // Recheck under the same user lock used for login/password changes. Only one consumer wins.
        entry = accounts.lockToken(digest, purpose, now());
        if (entry == null) throw invalidLink();
        return entry;
    }

    private AccountMailService.Outgoing tokenMail(Long id, String purpose, String email, int minutes,
                                                 String route, String subject) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        accounts.removePurpose(id, purpose);
        accounts.createToken(hash(token), id, purpose, email, now().plusMinutes(minutes));
        // Fragment stays out of HTTP access logs and Referer headers. The UI submits it via POST.
        String link = mailProperties.getPublicBaseUrl() + route + "#token=" + token;
        return new AccountMailService.Outgoing(email, subject,
                "请打开以下链接完成操作（" + minutes + "分钟内有效，仅可使用一次）：\n\n" + link
                        + "\n\n如非本人申请，请忽略此邮件。请勿向他人转发该链接。");
    }

    public static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public static String hash(String value) {
        return Hashing.sha256Hex(value);
    }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private static BusinessException invalidLink() {
        return new BusinessException(ResultCode.BAD_REQUEST, "链接无效或已过期，请重新申请");
    }
}
