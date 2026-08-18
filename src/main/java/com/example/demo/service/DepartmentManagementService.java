package com.example.demo.service;

import com.example.demo.dto.DepartmentCreateRequest;
import com.example.demo.dto.DepartmentResponse;
import com.example.demo.dto.DepartmentUpdateRequest;
import com.example.demo.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DepartmentManagementService {

    Page<DepartmentResponse> getDepartments(Pageable pageable, String currentUsername);

    DepartmentResponse getDepartmentById(Long id, String currentUsername);

    DepartmentResponse createDepartment(DepartmentCreateRequest request, String currentUsername);

    DepartmentResponse updateDepartment(Long id, DepartmentUpdateRequest request, String currentUsername);

    void deleteDepartment(Long id, String currentUsername);

    Department findById(Long id);

    List<Department> findByManagersId(Long userId);
}
