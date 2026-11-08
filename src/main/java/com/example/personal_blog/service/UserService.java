package com.example.personal_blog.service;

import com.example.personal_blog.dto.RegisterDTO;
import com.example.personal_blog.dto.UserProfile;

public interface UserService {
    UserProfile register(RegisterDTO dto);
    String login(String username, String password);
    UserProfile me();
}
