package com.example.personal_blog.dto;

import jakarta.validation.constraints.Positive;

public record ArticleCategoryDTO(@Positive Long categoryId) {
}
