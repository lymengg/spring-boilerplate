package com.example.demo.service;

import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.dto.PageResponse;
import com.example.demo.dto.UserCreateRequest;
import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserMfaToggleRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import org.springframework.data.domain.Pageable;

public interface UserManagementService {

    UserResponse createUser(UserCreateRequest request, String currentEmail);

    PageResponse<UserResponse> getUsers(Pageable pageable, String currentEmail);

    UserResponse getUserById(Long id, String currentEmail);

    UserResponse updateUser(Long id, UserUpdateRequest request, String currentEmail);

    void deleteUser(Long id, String currentEmail);

    UserResponse toggleUserEnabled(Long id, UserEnableRequest request, String currentEmail);

    UserResponse assignRole(Long id, UserRoleAssignmentRequest request, String currentEmail);

    UserResponse removeRole(Long id, UserRoleAssignmentRequest request, String currentEmail);

    MfaSetupResponse enableMfa(Long id, UserMfaToggleRequest request, String currentEmail);

    void disableMfa(Long id, String currentEmail);

    MfaSetupResponse resetMfa(Long id, UserMfaToggleRequest request, String currentEmail);
}
