package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResult;
import com.example.demo.dto.MfaVerifyRequest;
import com.example.demo.dto.TokenResponse;

public interface LoginService {

    LoginResult login(LoginRequest request, String ipAddress);

    TokenResponse verifyMfa(MfaVerifyRequest request, String ipAddress);
}
