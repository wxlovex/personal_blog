package com.example.personal_blog.utils;

import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;

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

    public static Long requireUserId() {
        Long id = getUserId();
        if (id == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return id;
    }

    public static void clear() {
        userThreadLocal.remove();
    }
}
