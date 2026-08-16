package com.example.demo.mapper;

import com.example.demo.dto.DepartmentResponse;
import com.example.demo.entity.Department;
import com.example.demo.entity.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DepartmentMapper {

    public DepartmentResponse toResponse(Department department) {
        List<Long> managerIds = new ArrayList<>();
        List<String> managerUsernames = new ArrayList<>();

        if (department.getManagers() != null) {
            department.getManagers().stream()
                    .sorted(Comparator.comparing(User::getId))
                    .forEach(manager -> {
                        managerIds.add(manager.getId());
                        managerUsernames.add(manager.getUsername());
                    });
        }

        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .tenantId(department.getTenant().getId())
                .tenantName(department.getTenant().getName())
                .managerIds(managerIds)
                .managerUsernames(managerUsernames)
                .build();
    }
}
