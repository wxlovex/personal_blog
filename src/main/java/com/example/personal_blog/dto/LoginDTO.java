package com.example.personal_blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(@NotBlank @Size(max = 32) String username,
                       @NotBlank @Size(max = 256) String password) {
}
