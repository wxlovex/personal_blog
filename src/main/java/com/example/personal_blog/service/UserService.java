package com.example.personal_blog.service;

import com.example.personal_blog.entity.User;

public interface UserService {
    void register(User user);
    String login(String username, String password);
}
