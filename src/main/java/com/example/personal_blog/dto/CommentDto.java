package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentDto {
    @NotNull(message = "文章ID不能为空")
    @Positive
    private Long articleId;

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "评论长度不能超过2000字")
    private String content;
}
