package com.example.demo.service;

import com.example.demo.dto.UserCreateRequest;
import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserManagementService {

    UserResponse createUser(UserCreateRequest request, String currentUsername);

    Page<UserResponse> getUsers(Pageable pageable, String currentUsername);

    UserResponse getUserById(Long id, String currentUsername);

    UserResponse updateUser(Long id, UserUpdateRequest request, String currentUsername);

    void deleteUser(Long id, String currentUsername);

    UserResponse toggleUserEnabled(Long id, UserEnableRequest request, String currentUsername);

    UserResponse assignRole(Long id, UserRoleAssignmentRequest request, String currentUsername);

    UserResponse removeRole(Long id, UserRoleAssignmentRequest request, String currentUsername);
}
