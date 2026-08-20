package com.example.demo.service;

import com.example.demo.dto.MfaDisableRequest;
import com.example.demo.dto.MfaEnableRequest;
import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.dto.MfaVerifySetupRequest;

public interface MfaSetupService {

    MfaSetupResponse enableMfa(String username, MfaEnableRequest request, String ipAddress);

    void verifyMfaSetup(String username, MfaVerifySetupRequest request, String ipAddress);

    void disableMfa(String username, MfaDisableRequest request, String ipAddress);
}
