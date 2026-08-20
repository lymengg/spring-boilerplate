package com.example.demo.dto;

import com.example.demo.entity.MfaMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMfaToggleRequest {

    @NotNull(message = "MFA method is required")
    private MfaMethod method;
}
