package com.example.personal_blog.dto;

import jakarta.validation.constraints.*;

public final class AccountRequests {
    private AccountRequests() {}
    public record CurrentPassword(@NotBlank @Size(max = 256) String currentPassword) {}
    public record ChangePassword(@NotBlank @Size(max = 256) String currentPassword,
                                 @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record VerifyEmailRequest(@NotBlank @Size(max = 256) String currentPassword,
                                     @NotBlank @Email @Size(max = 254) String email) {}
    public record ForgotPassword(@NotBlank @Email @Size(max = 254) String email) {}
    public record Token(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {}
    public record ResetPassword(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
                                @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
