package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.config.PasswordConfig;
import com.example.personal_blog.entity.User;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.service.UserService;
import com.example.personal_blog.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * 注册接口
     * @param user
     */
    @Override
    public void register(User user) {
        String rawPassword = user.getPassword();
        //  加密
        String encodedPassword = passwordEncoder.encode(rawPassword);

        user.setPassword(encodedPassword);

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
        );

        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED,"用户名错误");
        }
        // 加密
        boolean matches = passwordEncoder.matches(password, user.getPassword());

        if(!matches){
            throw new BusinessException(ResultCode.UNAUTHORIZED,"密码错误");
        }

        // 生成JWT
        return JwtUtil.generateToken(user.getId(),user.getUsername());
    }
}
