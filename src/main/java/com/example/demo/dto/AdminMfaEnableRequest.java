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
public class AdminMfaEnableRequest {

    @NotNull(message = "Target user ID is required")
    private Long targetUserId;

    @NotNull(message = "MFA method is required")
    private MfaMethod method;
}
