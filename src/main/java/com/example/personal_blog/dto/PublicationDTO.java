package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PublicationDTO(@NotNull Boolean published,
                             @Pattern(regexp = "技术|日常|读书|娱乐", message = "请选择有效的广场分类") String category) {
}
