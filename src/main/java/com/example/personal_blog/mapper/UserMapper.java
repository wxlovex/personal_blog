package com.example.personal_blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.personal_blog.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id FROM `user` WHERE id = #{id} FOR UPDATE")
    Long lockId(Long id);

    @Select("SELECT * FROM `user` WHERE id = #{id} FOR UPDATE")
    User lockById(Long id);
}
