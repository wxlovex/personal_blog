package com.example.personal_blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
    private String title;
    private String content;
    private Long viewCount;
    private Long likeCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean published;
    private String plazaCategory;
    private LocalDateTime publishedTime;
    @TableField(exist = false)
    private String authorName;
    @TableField(exist = false)
    private String categoryName;
    @TableField(exist = false)
    private Boolean liked;
    @TableField(exist = false)
    private Boolean subscribed;
    @TableField(exist = false)
    private String summary;
}
