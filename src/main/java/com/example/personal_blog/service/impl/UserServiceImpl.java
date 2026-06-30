package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.personal_blog.entity.User;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.service.UserService;
import com.example.personal_blog.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    /**
     * 注册接口
     * @param user
     */
    @Override
    public void register(User user) {
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    /**
     * 登录接口
     * @param username
     * @param password
     * @return
     */
    @Override
    public String login(String username, String password) {
        User user = userMapper.selectOne(
                new QueryWrapper<User>()
                        .eq("username", username)
                        .eq("password", password)
        );

        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 生成JWT
        return JwtUtil.generateToken(user.getId(),user.getUsername());
    }
}
