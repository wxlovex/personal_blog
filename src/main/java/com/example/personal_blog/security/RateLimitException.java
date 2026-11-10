package com.example.personal_blog.security;

public class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super("操作过于频繁，请在 " + retryAfterSeconds + " 秒后重试");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
