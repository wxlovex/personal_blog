package com.example.personal_blog.utils;

/**
 * 共享和获取当前登录用户的 ID, 避免层层传递
 */
public class UserContext {
    private static final ThreadLocal<Long> userThreadLocal = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userThreadLocal.set(userId);
    }

    public static Long getUserId() {
        return userThreadLocal.get();
    }

    public static void clear() {
        userThreadLocal.remove();
    }
}
