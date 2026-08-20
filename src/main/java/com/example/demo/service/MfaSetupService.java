package com.example.demo.service;

import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.User;

public interface MfaSetupService {

    MfaSetupResponse enableMfa(User targetUser, MfaMethod method, String ipAddress);

    void disableMfa(User targetUser, String ipAddress);

    MfaSetupResponse resetMfa(User targetUser, MfaMethod method, String ipAddress);
}
