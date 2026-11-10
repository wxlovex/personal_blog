package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArticleDTO {
    @Size(max = 50, message = "标题长度不能超过50字")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 100000, message = "内容长度不能超过100000字")
    private String content;

    @Positive
    private Long categoryId;
}
