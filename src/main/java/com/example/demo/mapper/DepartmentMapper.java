package com.example.demo.mapper;

import com.example.demo.dto.DepartmentResponse;
import com.example.demo.entity.Department;
import org.springframework.stereotype.Component;

@Component
public class DepartmentMapper {

    public DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .tenantId(department.getTenant().getId())
                .tenantName(department.getTenant().getName())
                .managerId(department.getManager() != null ? department.getManager().getId() : null)
                .managerUsername(department.getManager() != null ? department.getManager().getUsername() : null)
                .build();
    }
}
