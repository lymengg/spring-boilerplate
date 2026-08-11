package com.example.demo.service;

import com.example.demo.constants.AuditActions;
import com.example.demo.dto.ExpenseCreateRequest;
import com.example.demo.dto.ExpenseUpdateRequest;
import com.example.demo.entity.Department;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.ExpenseMapper;
import com.example.demo.repository.ExpenseRepository;
import com.example.demo.security.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private DepartmentManagementService departmentManagementService;

    @Mock
    private UserService userService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ExpenseMapper expenseMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ExpenseService expenseService;

    private Tenant tenant;
    private Department department;
    private User employee;
    private User otherEmployee;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("Tenant").build();
        department = Department.builder().id(10L).name("Dept").tenant(tenant).build();

        employee = userWithUsername("employee");
        employee.setTenant(tenant);
        employee.setDepartment(department);

        otherEmployee = userWithUsername("other");
        otherEmployee.setTenant(tenant);
        otherEmployee.setDepartment(department);
    }

    @Test
    @DisplayName("Creating an expense assigns current user's tenant and department")
    void createExpenseAssignsTenantAndDepartment() {
        ExpenseCreateRequest request = ExpenseCreateRequest.builder()
                .title("Meal")
                .description("Lunch")
                .amount(BigDecimal.valueOf(25.00))
                .category("Food")
                .build();

        when(userService.getByUsername("employee")).thenReturn(employee);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        expenseService.createExpense(request, "employee");

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getTenant()).isEqualTo(tenant);
        assertThat(saved.getDepartment()).isEqualTo(department);
        assertThat(saved.getOwner()).isEqualTo(employee);
        assertThat(saved.getStatus()).isEqualTo(ExpenseStatus.PENDING);
    }

    @Test
    @DisplayName("Creating an expense fails when user has no tenant")
    void createExpenseFailsWithoutTenant() {
        employee.setTenant(null);

        ExpenseCreateRequest request = ExpenseCreateRequest.builder()
                .title("Meal")
                .description("Lunch")
                .amount(BigDecimal.valueOf(25.00))
                .category("Food")
                .build();

        when(userService.getByUsername("employee")).thenReturn(employee);

        assertThatThrownBy(() -> expenseService.createExpense(request, "employee"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must belong to a tenant");
    }

    @Test
    @DisplayName("Tenant-scoped employee only sees their own expenses")
    void getExpensesFiltersByOwnerForEmployee() {
        when(userService.getByUsername("employee")).thenReturn(employee);
        when(authorizationService.isSuperAdmin(employee)).thenReturn(false);
        when(authorizationService.hasAuthority(employee, com.example.demo.constants.Authorities.AUDIT_LOG_READ)).thenReturn(false);
        when(authorizationService.hasAuthority(employee, com.example.demo.constants.Authorities.EXPENSE_APPROVE)).thenReturn(false);
        when(authorizationService.hasAuthority(employee, com.example.demo.constants.Authorities.EXPENSE_PROCESS)).thenReturn(false);

        PageRequest pageable = PageRequest.of(0, 10);
        when(expenseRepository.findAllByOwnerId(employee.getId(), pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        Page<?> result = expenseService.getExpenses(pageable, "employee");

        assertThat(result).isEmpty();
        verify(expenseRepository).findAllByOwnerId(employee.getId(), pageable);
    }

    @Test
    @DisplayName("Employee cannot update another user's expense")
    void employeeCannotUpdateOtherExpense() {
        Expense expense = Expense.builder()
                .id(1L)
                .title("Travel")
                .status(ExpenseStatus.PENDING)
                .owner(otherEmployee)
                .department(department)
                .tenant(tenant)
                .build();

        when(userService.getByUsername("employee")).thenReturn(employee);
        when(authorizationService.isSuperAdmin(employee)).thenReturn(false);
        when(expenseRepository.findByIdAndTenantId(1L, tenant.getId())).thenReturn(Optional.of(expense));
        when(authorizationService.canEditExpense(employee, expense)).thenReturn(false);

        ExpenseUpdateRequest request = ExpenseUpdateRequest.builder()
                .title("Updated")
                .description("Updated")
                .amount(BigDecimal.valueOf(10.00))
                .category("Travel")
                .build();

        assertThatThrownBy(() -> expenseService.updateExpense(1L, request, "employee"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User userWithUsername(String username) {
        User user = User.builder()
                .id((long) username.hashCode())
                .username(username)
                .password("secret")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        user.setRoles(new HashSet<>());
        user.getRoles().add(Role.builder().name("USER").permissions(new HashSet<>()).build());
        return user;
    }

    private Long nullableLong() {
        return null;
    }
}
