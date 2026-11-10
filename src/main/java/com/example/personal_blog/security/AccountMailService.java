package com.example.personal_blog.security;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.RejectedExecutionException;

/** Bounded asynchronous SMTP delivery; no tokens are returned by API or written to logs. */
@Service
@Slf4j
public class AccountMailService {
    public record Outgoing(String to, String subject, String body) {}
    private final JavaMailSender sender;
    private final AccountMailProperties properties;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    public AccountMailService(JavaMailSender sender, AccountMailProperties properties) {
        this.sender = sender;
        this.properties = properties;
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("account-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
    }

    public void requireEnabled() {
        if (!properties.isEnabled()) throw new BusinessException(ResultCode.UNAVAILABLE, "邮件服务暂未启用，请稍后重试");
    }

    public void dispatch(Outgoing outgoing) {
        requireEnabled();
        try {
            executor.execute(() -> {
                if (outgoing == null) return;
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.getFrom());
                message.setTo(outgoing.to());
                message.setSubject(outgoing.subject());
                message.setText(outgoing.body());
                try { sender.send(message); }
                catch (RuntimeException e) { log.warn("Account email delivery failed; check SMTP availability"); }
            });
        } catch (RejectedExecutionException e) {
            throw new BusinessException(ResultCode.UNAVAILABLE, "邮件请求繁忙，请稍后重试");
        }
    }

    public void notifySecurityEvent(String address, String event) {
        if (address == null || !properties.isEnabled()) return;
        try {
            dispatch(new Outgoing(address, "行间 · 安全提醒", event + "。\n\n若非本人操作，请立即通过已验证邮箱找回账号：\n"
                    + properties.getPublicBaseUrl() + "/forgot-password"));
        } catch (BusinessException e) { log.warn("Account security notification queue is full"); }
    }

    @PreDestroy
    public void close() { executor.shutdown(); }
}
