package com.example.personal_blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_subscription")
public class UserSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long subscriberId;
    private Long authorId;
    private LocalDateTime createTime;
}
