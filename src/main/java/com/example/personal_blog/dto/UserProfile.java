package com.example.personal_blog.dto;

import com.example.personal_blog.entity.User;
import java.time.LocalDateTime;

public record UserProfile(Long id, String username, String email, LocalDateTime createTime) {
    public static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getEmail(), user.getCreateTime());
    }
}
