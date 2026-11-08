package com.example.personal_blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.personal_blog.common.BusinessException;
import com.example.personal_blog.common.ResultCode;
import com.example.personal_blog.dto.RegisterDTO;
import com.example.personal_blog.dto.UserProfile;
import com.example.personal_blog.entity.User;
import com.example.personal_blog.mapper.UserMapper;
import com.example.personal_blog.service.UserService;
import com.example.personal_blog.utils.JwtUtil;
import com.example.personal_blog.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public UserProfile register(RegisterDTO dto) {
        if (dto.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码的UTF-8编码不能超过72字节");
        }
        if (userMapper.selectCount(new QueryWrapper<User>().eq("username", dto.username())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.username());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setEmail(dto.email() == null || dto.email().isBlank() ? null : dto.email());
        user.setCreateTime(LocalDateTime.now(java.time.ZoneOffset.UTC));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        return UserProfile.from(user);
    }

    @Override
    public String login(String username, String password) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public UserProfile me() {
        User user = userMapper.selectById(UserContext.requireUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        return UserProfile.from(user);
    }
}
