package com.example.demo.dto;

import com.example.demo.constants.UserPermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String title;
    private String description;
    private Set<UserPermission> permissions;
}
