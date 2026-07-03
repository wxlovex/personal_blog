package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArticleDTO {
    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题长度不能超过50字")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;
}
