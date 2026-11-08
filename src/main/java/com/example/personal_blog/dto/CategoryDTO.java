package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryDTO(@NotBlank @Size(max = 40) String name,
                          @Size(max = 200) String description) {
}
