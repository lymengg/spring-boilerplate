package com.example.demo.service;

import com.example.demo.dto.AdminMfaDisableRequest;
import com.example.demo.dto.AdminMfaEnableRequest;
import com.example.demo.dto.AdminMfaResetRequest;
import com.example.demo.dto.MfaEnableRequest;
import com.example.demo.dto.MfaSetupResponse;

public interface MfaSetupService {

    MfaSetupResponse enableMfa(String username, MfaEnableRequest request, String ipAddress);

    MfaSetupResponse enableMfaForUser(String adminUsername, AdminMfaEnableRequest request, String ipAddress);

    MfaSetupResponse resetMfa(String adminUsername, AdminMfaResetRequest request, String ipAddress);

    void disableMfaForUser(String adminUsername, AdminMfaDisableRequest request, String ipAddress);
}
