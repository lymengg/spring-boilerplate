package com.example.demo.repository;

import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findAllByTenantId(Long tenantId, Pageable pageable);

    Page<Expense> findAllByOwnerId(Long ownerId, Pageable pageable);

    Page<Expense> findAllByDepartmentId(Long departmentId, Pageable pageable);

    Page<Expense> findAllByDepartmentIdIn(Collection<Long> departmentIds, Pageable pageable);

    Page<Expense> findAllByTenantIdAndStatus(Long tenantId, ExpenseStatus status, Pageable pageable);

    Optional<Expense> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);
}
