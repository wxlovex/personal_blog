package com.example.personal_blog.controller;

import com.example.personal_blog.common.PublicEndpoint;
import com.example.personal_blog.common.Result;
import com.example.personal_blog.dto.LoginDTO;
import com.example.personal_blog.dto.RegisterDTO;
import com.example.personal_blog.dto.UserProfile;
import com.example.personal_blog.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/user", "/api/user"})
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PublicEndpoint(ignoreToken = true)
    @PostMapping("/register")
    public Result<UserProfile> register(@RequestBody @Valid RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @PublicEndpoint(ignoreToken = true)
    @PostMapping("/login")
    public Result<String> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success(userService.login(dto.username(), dto.password()));
    }

    @GetMapping("/me")
    public Result<UserProfile> me() {
        return Result.success(userService.me());
    }
}
