package com.example.personal_blog.controller;

import com.example.personal_blog.common.Result;
import com.example.personal_blog.entity.User;
import com.example.personal_blog.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public String register(@RequestBody User user) {
        userService.register(user);
        return "注册成功";
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody User user) {
        String token = userService.login(user.getUsername(), user.getPassword());
        return Result.success(token);
    }
}
