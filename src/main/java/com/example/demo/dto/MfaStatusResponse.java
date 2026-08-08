package com.example.demo.dto;

import com.example.demo.entity.MfaMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaStatusResponse {

    private boolean mfaEnabled;
    private MfaMethod method;
}
