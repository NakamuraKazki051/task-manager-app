package com.taskmanager.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            // BCrypt silently ignores input past 72 bytes, so cap here to fail loudly instead
            @NotBlank @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください") String password
    ) {
    }

    public record UpdateRequest(
            @Email String email,
            @NotBlank String currentPassword,
            @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください") String newPassword
    ) {
    }

    public record UserResponse(String id, String email, Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
        }
    }
}
