package com.example.personal_blog.controller;

import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.AccountRequests.*;
import com.example.personal_blog.dto.AccountSecurityStatus;
import com.example.personal_blog.security.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/user", "/api/user"})
public class AccountSecurityController {
    private final AccountSecurityService accounts;
    private final AccountMailService mail;
    private final AccountMailProperties mailProperties;

    @GetMapping("/security")
    public Result<AccountSecurityStatus> status() { return Result.success(accounts.status()); }

    @PublicEndpoint(ignoreToken = true)
    @GetMapping("/security-options")
    public Result<Map<String, Boolean>> options() {
        return Result.success(Map.of("mailEnabled", mailProperties.isEnabled()));
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePassword request) {
        mail.notifySecurityEvent(accounts.changePassword(request), "账号密码已修改");
        return Result.success();
    }

    @PostMapping("/logout-all")
    public Result<Void> logoutAll(@RequestBody @Valid CurrentPassword request) {
        accounts.logoutAll(request);
        return Result.success();
    }

    @PostMapping("/email/verification")
    public Result<Void> requestVerification(@RequestBody @Valid VerifyEmailRequest request) {
        mail.dispatch(accounts.prepareVerification(request));
        return Result.success();
    }

    @PublicEndpoint(ignoreToken = true)
    @PostMapping("/email/confirm")
    public Result<Long> confirmEmail(@RequestBody @Valid Token request) {
        var result = accounts.confirmEmail(request.token());
        mail.notifySecurityEvent(result.notifyEmail(), "账号验证邮箱已更换");
        return Result.success(result.userId());
    }

    @PublicEndpoint(ignoreToken = true)
    @PostMapping("/password/forgot")
    public Result<Void> forgotPassword(@RequestBody @Valid ForgotPassword request) {
        // Same response and bounded async queue, whether the verified address exists or not.
        mail.dispatch(accounts.prepareReset(request.email()));
        return Result.success();
    }

    @PublicEndpoint(ignoreToken = true)
    @PostMapping("/password/reset")
    public Result<Long> resetPassword(@RequestBody @Valid ResetPassword request) {
        var result = accounts.resetPassword(request);
        mail.notifySecurityEvent(result.notifyEmail(), "账号密码已重置");
        return Result.success(result.userId());
    }
}
