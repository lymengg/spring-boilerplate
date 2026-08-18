package com.example.demo.service;

import com.example.demo.entity.User;

public interface AccountLockoutService {

    User prepareForLogin(User user, String ipAddress);

    void recordFailedLogin(User user, String ipAddress);

    void recordSuccessfulLogin(User user, String ipAddress);
}
