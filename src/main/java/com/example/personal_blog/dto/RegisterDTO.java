package com.example.personal_blog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDTO(
        @NotBlank @Pattern(regexp = "[\\p{L}\\p{N}_-]{3,32}", message = "用户名需为3至32位字母、数字、下划线或短横线") String username,
        @NotBlank @Size(min = 8, max = 72, message = "密码长度需为8至72个字符") String password,
        @Email(message = "邮箱格式不正确") @Size(max = 254) String email) {
}
