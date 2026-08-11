package com.example.demo.mapper;

import com.example.demo.dto.RoleResponse;
import com.example.demo.entity.Role;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RoleMapper {

    public RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(Set.copyOf(role.getPermissions()))
                .build();
    }
}
