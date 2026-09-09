package com.example.demo.service;

import com.example.demo.dto.DepartmentCreateRequest;
import com.example.demo.dto.DepartmentResponse;
import com.example.demo.dto.DepartmentUpdateRequest;
import com.example.demo.dto.PageResponse;
import com.example.demo.entity.Department;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DepartmentManagementService {

    PageResponse<DepartmentResponse> getDepartments(Pageable pageable, String currentEmail);

    DepartmentResponse getDepartmentById(Long id, String currentEmail);

    DepartmentResponse createDepartment(DepartmentCreateRequest request, String currentEmail);

    DepartmentResponse updateDepartment(Long id, DepartmentUpdateRequest request, String currentEmail);

    void deleteDepartment(Long id, String currentEmail);

    Department findById(Long id);

    List<Department> findByManagersId(Long userId);
}
