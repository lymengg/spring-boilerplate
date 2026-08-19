package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMfaDisableRequest {

    @NotNull(message = "Target user ID is required")
    private Long targetUserId;
}
