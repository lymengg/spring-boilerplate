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
    public ResponseEntity<ApiResponse<MfaSetupResponse>> enableMfa(
            @Valid @RequestBody MfaEnableRequest request,
            Authentication authentication) {
        MfaSetupResponse response = authService.enableMfa(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("MFA enabled successfully", response));
    }

    @PostMapping("/admin/enable")
    @PreAuthorize("hasAuthority('MFA_MANAGE')")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> enableMfaForUser(
            @Valid @RequestBody AdminMfaEnableRequest request,
            Authentication authentication) {
        MfaSetupResponse response = authService.enableMfaForUser(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("MFA enabled successfully for user", response));
    }

    @PostMapping("/admin/reset")
    @PreAuthorize("hasAuthority('MFA_MANAGE')")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> resetMfa(
            @Valid @RequestBody AdminMfaResetRequest request,
            Authentication authentication) {
        MfaSetupResponse response = authService.resetMfa(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("MFA reset successfully", response));
    }

    @PostMapping("/admin/disable")
    @PreAuthorize("hasAuthority('MFA_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> disableMfaForUser(
            @Valid @RequestBody AdminMfaDisableRequest request,
            Authentication authentication) {
        authService.disableMfaForUser(authentication, request);
        return ResponseEntity.ok(ApiResponse.success("MFA disabled successfully", null));
    }
}
