package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PublicationDTO(@NotNull Boolean published,
                             @Size(max = 20, message = "广场分类长度不能超过20字") String category) {
}
