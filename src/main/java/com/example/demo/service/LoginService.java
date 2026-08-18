package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.MfaVerifyRequest;
import com.example.demo.dto.TokenResponse;

public interface LoginService {

    Object login(LoginRequest request, String ipAddress);

    TokenResponse verifyMfa(MfaVerifyRequest request, String ipAddress);
}
