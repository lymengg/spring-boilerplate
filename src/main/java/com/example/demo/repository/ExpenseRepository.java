package com.example.demo.repository;

import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @EntityGraph(attributePaths = {"owner", "department", "tenant", "approvedBy", "rejectedBy", "processedBy"})
    Optional<Expense> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"owner", "department", "tenant", "approvedBy", "rejectedBy", "processedBy"})
    @Query("SELECT e FROM Expense e " +
            "WHERE (:tenantId IS NULL OR e.tenant.id = :tenantId) " +
            "AND (:departmentId IS NULL OR e.department.id = :departmentId) " +
            "AND (:status IS NULL OR e.status = :status)")
    Page<Expense> findAllWithFilters(@Param("tenantId") Long tenantId,
                                     @Param("departmentId") Long departmentId,
                                     @Param("status") ExpenseStatus status,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "department", "tenant", "approvedBy", "rejectedBy", "processedBy"})
    @Query("SELECT e FROM Expense e WHERE e.department.id = :departmentId " +
            "AND (:status IS NULL OR e.status = :status)")
    Page<Expense> findByDepartmentIdWithStatus(@Param("departmentId") Long departmentId,
                                                @Param("status") ExpenseStatus status,
                                                Pageable pageable);
}
