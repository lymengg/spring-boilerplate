package com.example.demo.service;

import com.example.demo.constants.AuditActions;
import com.example.demo.dto.ExpenseCreateRequest;
import com.example.demo.dto.ExpenseResponse;
import com.example.demo.dto.ExpenseUpdateRequest;
import com.example.demo.entity.Department;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.User;
import com.example.demo.mapper.ExpenseMapper;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.ExpenseRepository;
import com.example.demo.constants.Authorities;
import com.example.demo.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentManagementService departmentManagementService;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final ExpenseMapper expenseMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public Page<ExpenseResponse> getExpenses(Pageable pageable, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        if (authorizationService.isSuperAdmin(currentUser)) {
            return expenseRepository.findAll(pageable).map(expenseMapper::toResponse);
        }
        if (currentUser.getTenant() == null) {
            return Page.empty(pageable);
        }
        Long tenantId = currentUser.getTenant().getId();
        if (authorizationService.hasAuthority(currentUser, Authorities.AUDIT_LOG_READ)) {
            return expenseRepository.findAllByTenantId(tenantId, pageable).map(expenseMapper::toResponse);
        }
        if (authorizationService.hasAuthority(currentUser, Authorities.EXPENSE_APPROVE)) {
            List<Long> managedDeptIds = departmentRepository.findByManagersId(currentUser.getId())
                    .stream()
                    .map(Department::getId)
                    .toList();
            if (!managedDeptIds.isEmpty()) {
                return expenseRepository.findAllByDepartmentIdIn(managedDeptIds, pageable)
                        .map(expenseMapper::toResponse);
            }
        }
        if (authorizationService.hasAuthority(currentUser, Authorities.EXPENSE_PROCESS)) {
            return expenseRepository.findAllByTenantIdAndStatus(tenantId, ExpenseStatus.APPROVED, pageable)
                    .map(expenseMapper::toResponse);
        }
        return expenseRepository.findAllByOwnerId(currentUser.getId(), pageable)
                .map(expenseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    public ExpenseResponse getExpenseById(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Expense expense = findAccessibleExpense(id, currentUser);
        if (!authorizationService.canViewExpense(currentUser, expense)) {
            throw new AccessDeniedException("Cannot view this expense");
        }
        return expenseMapper.toResponse(expense);
    }

    @Transactional
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    public ExpenseResponse createExpense(ExpenseCreateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        if (currentUser.getTenant() == null) {
            throw new IllegalArgumentException("User must belong to a tenant to create an expense");
        }
        Department department = resolveDepartment(request.getDepartmentId(), currentUser);
        Expense expense = Expense.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .amount(request.getAmount())
                .category(request.getCategory())
                .status(ExpenseStatus.PENDING)
                .owner(currentUser)
                .department(department)
                .tenant(currentUser.getTenant())
                .build();
        Expense saved = expenseRepository.save(expense);
        recordExpenseEvent(saved, currentUsername, AuditActions.EXPENSE_CREATED, "Expense created");
        return expenseMapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('EXPENSE_UPDATE')")
    public ExpenseResponse updateExpense(Long id, ExpenseUpdateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Expense expense = findAccessibleExpense(id, currentUser);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new IllegalStateException("Only pending expenses can be updated");
        }
        if (!authorizationService.canEditExpense(currentUser, expense)) {
            throw new AccessDeniedException("Cannot edit this expense");
        }
        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setCategory(request.getCategory());
        Expense updated = expenseRepository.save(expense);
        recordExpenseEvent(updated, currentUsername, AuditActions.EXPENSE_UPDATED, "Expense updated");
        return expenseMapper.toResponse(updated);
    }

    @Transactional
    @PreAuthorize("hasAuthority('EXPENSE_UPDATE')")
    public ExpenseResponse cancelExpense(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Expense expense = findAccessibleExpense(id, currentUser);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new IllegalStateException("Only pending expenses can be cancelled");
        }
        if (!authorizationService.canCancelExpense(currentUser, expense)) {
            throw new AccessDeniedException("Cannot cancel this expense");
        }
        expense.setStatus(ExpenseStatus.CANCELLED);
        Expense cancelled = expenseRepository.save(expense);
        recordExpenseEvent(cancelled, currentUsername, AuditActions.EXPENSE_CANCELLED, "Expense cancelled");
        return expenseMapper.toResponse(cancelled);
    }

    @Transactional
    public Expense save(Expense expense) {
        return expenseRepository.save(expense);
    }

    Expense findAccessibleExpense(Long id, User user) {
        if (authorizationService.isSuperAdmin(user)) {
            return expenseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        }
        if (user.getTenant() != null) {
            return expenseRepository.findByIdAndTenantId(id, user.getTenant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        }
        throw new AccessDeniedException("Cannot access this expense");
    }

    private void recordExpenseEvent(Expense expense, String actorUsername, String action, String details) {
        auditLogService.record(action, AuditActions.RESOURCE_EXPENSE, String.valueOf(expense.getId()), details, actorUsername);
    }

    private Department resolveDepartment(Long departmentId, User user) {
        if (departmentId == null) {
            return user.getDepartment();
        }
        Department department = departmentManagementService.findById(departmentId);
        if (user.getTenant() == null || !user.getTenant().getId().equals(department.getTenant().getId())) {
            throw new IllegalArgumentException("Department must belong to the same tenant");
        }
        return department;
    }
}
