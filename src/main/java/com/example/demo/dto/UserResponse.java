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
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean enabled;
    private Boolean accountNonLocked;
    private Set<String> roles;
    private Set<String> permissions;
    private Boolean mfaEnabled;
    private String mfaMethod;
    private Instant createdAt;
    private Instant updatedAt;
}
