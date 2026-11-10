package com.example.personal_blog.dto;

import com.example.personal_blog.entity.User;
import java.time.LocalDateTime;

public record UserProfile(Long id, String username, String email, LocalDateTime createTime, boolean emailVerified) {
    public static UserProfile from(User user) {
        return from(user, false);
    }
    public static UserProfile from(User user, boolean verified) {
        return new UserProfile(user.getId(), user.getUsername(), user.getEmail(), user.getCreateTime(), verified);
    }
}
