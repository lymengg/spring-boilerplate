package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final AuthService authService;

    @PostMapping("/enable")
    @PreAuthorize("hasAuthority('MFA_MANAGE')")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> enableMfa(
            @Valid @RequestBody MfaEnableRequest request,
            Authentication authentication) {
        MfaSetupResponse response = authService.enableMfa(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("MFA setup initiated", response));
    }

    @PostMapping("/verify-setup")
    @PreAuthorize("hasAuthority('MFA_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> verifyMfaSetup(
            @Valid @RequestBody MfaVerifySetupRequest request,
            Authentication authentication) {
        authService.verifyMfaSetup(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("MFA enabled successfully", null));
    }

    @PostMapping("/disable")
    @PreAuthorize("hasAuthority('MFA_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> disableMfa(
            @Valid @RequestBody MfaDisableRequest request,
            Authentication authentication) {
        authService.disableMfa(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("MFA disabled successfully", null));
    }
}
