package com.example.demo.repository;

import com.example.demo.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Page<Department> findAllByTenantId(Long tenantId, Pageable pageable);

    List<Department> findAllByTenantId(Long tenantId);

    Optional<Department> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Department> findByNameAndTenantId(String name, Long tenantId);

    boolean existsByNameAndTenantId(String name, Long tenantId);
}
