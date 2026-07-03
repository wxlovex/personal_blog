package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommentDto {
    private Long articleId;

    @NotBlank(message = "内容不能为空")
    private String content;
}
