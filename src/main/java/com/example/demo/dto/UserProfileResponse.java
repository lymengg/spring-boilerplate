package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String email;
    private String firstName;
    private String lastName;
    private String[] roles;
    private Set<String> permissions;
    private Boolean enabled;
    private Boolean mfaEnabled;
    private String mfaMethod;
}
