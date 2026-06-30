package com.example.personal_blog.dto;

import lombok.Data;

@Data
public class CommentDto {
    private Long articleId;
    private String content;
}
