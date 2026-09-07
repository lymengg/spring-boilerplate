package com.example.demo.dto;

import com.example.demo.validation.Password;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(max = 100, message = "Password must not exceed 100 characters")
    @Password
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}