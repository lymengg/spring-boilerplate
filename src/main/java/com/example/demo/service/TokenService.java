package com.example.demo.service;

import com.example.demo.dto.TokenResponse;
import com.example.demo.entity.User;

public interface TokenService {

    TokenResponse generateTokenResponse(User user);

    TokenResponse refreshToken(String refreshToken, String ipAddress);

    void logout(String username, String accessToken, String ipAddress);

    void revokeAllUserRefreshTokens(String username);
}
