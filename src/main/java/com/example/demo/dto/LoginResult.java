package com.example.demo.dto;

public sealed interface LoginResult {

    record TokenSuccess(TokenResponse tokenResponse) implements LoginResult {}

    record MfaChallenge(MfaLoginResponse mfaResponse) implements LoginResult {}
}
