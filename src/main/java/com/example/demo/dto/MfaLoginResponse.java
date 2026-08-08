package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaLoginResponse {

    private boolean mfaRequired;
    private String mfaSessionToken;
    private String method;
    private long expiresIn;
}
