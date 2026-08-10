package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManagementResponse {

    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean accountNonLocked;
    private Set<String> roles;
    private Set<String> permissions;
    private Instant createdAt;
    private Instant updatedAt;
}
