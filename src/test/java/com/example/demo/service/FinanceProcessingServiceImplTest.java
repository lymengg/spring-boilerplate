package com.example.demo.service;

import com.example.demo.constants.AuditActions;
import com.example.demo.dto.ExpenseResponse;
import com.example.demo.entity.Department;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.ExpenseMapper;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.impl.FinanceProcessingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceProcessingServiceImplTest {

    @Mock
    private ExpenseService expenseService;

    @Mock
    private UserService userService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ExpenseMapper expenseMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private FinanceProcessingServiceImpl financeProcessingService;

    private Tenant tenant;
    private Tenant otherTenant;
    private Department department;
    private User approver;
    private User otherUser;
    private User crossTenantUser;
    private ExpenseResponse stubResponse;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("Tenant 1").build();
        otherTenant = Tenant.builder().id(2L).name("Tenant 2").build();
        department = Department.builder().id(10L).name("Dept").tenant(tenant).build();

        approver = userWithUsername("approver");
        approver.setTenant(tenant);
        approver.setDepartment(department);

        otherUser = userWithUsername("otherUser");
        otherUser.setTenant(tenant);
        otherUser.setDepartment(department);

        crossTenantUser = userWithUsername("crossTenantUser");
        crossTenantUser.setTenant(otherTenant);

        stubResponse = ExpenseResponse.builder().id(100L).build();

        when(userService.getByEmail("approver")).thenReturn(approver);
        when(userService.getByEmail("otherUser")).thenReturn(otherUser);
        when(userService.getByEmail("crossTenantUser")).thenReturn(crossTenantUser);
        when(expenseService.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(expenseMapper.toResponse(any(Expense.class))).thenReturn(stubResponse);
    }

    @Test
    @DisplayName("Processing an approved expense sets PROCESSED, processed date and processor, saves and audits")
    void processApprovedExpenseSetsProcessedState() {
        Expense expense = approvedExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canProcessExpense(approver, expense)).thenReturn(true);

        ExpenseResponse result = financeProcessingService.processExpense(100L, "approver");

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseService).save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ExpenseStatus.PROCESSED);
        assertThat(saved.getProcessedDate()).isNotNull();
        assertThat(saved.getProcessedBy()).isEqualTo(approver);
        verify(auditLogService).record(AuditActions.EXPENSE_PROCESSED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense processed for payment", "approver");
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Processing a pending expense throws IllegalStateException")
    void processPendingExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.PENDING);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only approved expenses can be processed");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.PENDING);
    }

    @Test
    @DisplayName("Processing a rejected expense throws IllegalStateException")
    void processRejectedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.REJECTED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only approved expenses can be processed");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.REJECTED);
    }

    @Test
    @DisplayName("Processing a cancelled expense throws IllegalStateException")
    void processCancelledExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.CANCELLED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only approved expenses can be processed");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.CANCELLED);
    }

    @Test
    @DisplayName("Processing an already processed expense throws IllegalStateException (double-payment guard)")
    void processProcessedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.PROCESSED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only approved expenses can be processed");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.PROCESSED);
    }

    @Test
    @DisplayName("Failed transitions never persist, audit or mutate the expense")
    void failedTransitionsHaveNoSideEffects() {
        for (ExpenseStatus status : List.of(ExpenseStatus.PENDING, ExpenseStatus.REJECTED,
                ExpenseStatus.CANCELLED, ExpenseStatus.PROCESSED)) {
            Expense expense = expenseWithStatus(status);
            when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

            assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(expense.getStatus()).isEqualTo(status);
        }
        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Processing without EXPENSE_PROCESS authority throws AccessDeniedException")
    void processWithoutAuthorityDenied() {
        Expense expense = approvedExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canProcessExpense(approver, expense)).thenReturn(false);

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot process this expense");

        verify(authorizationService).canProcessExpense(approver, expense);
        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Super admin can process an expense (bypasses authority checks)")
    void superAdminCanProcessExpense() {
        approver.setTenant(null);
        Expense expense = approvedExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canProcessExpense(approver, expense)).thenReturn(true);

        ExpenseResponse result = financeProcessingService.processExpense(100L, "approver");

        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.PROCESSED);
        assertThat(expense.getProcessedBy()).isEqualTo(approver);
        verify(expenseService).save(expense);
        verify(auditLogService).record(AuditActions.EXPENSE_PROCESSED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense processed for payment", "approver");
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Authorization failures never persist or audit the expense")
    void authorizationFailureHasNoSideEffects() {
        Expense expense = approvedExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canProcessExpense(approver, expense)).thenReturn(false);

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class);

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
    }

    @Test
    @DisplayName("Processing a nonexistent expense propagates IllegalArgumentException")
    void processNonexistentExpenseFails() {
        when(expenseService.findAccessibleExpense(100L, approver))
                .thenThrow(new IllegalArgumentException("Expense not found"));

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expense not found");

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Cross-tenant expense is treated as not-found and propagates IllegalArgumentException")
    void crossTenantExpenseTreatedAsNotFound() {
        when(expenseService.findAccessibleExpense(100L, crossTenantUser))
                .thenThrow(new IllegalArgumentException("Expense not found"));

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "crossTenantUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expense not found");

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("User with no tenant gets AccessDeniedException when processing")
    void userWithoutTenantCannotProcess() {
        approver.setTenant(null);
        when(expenseService.findAccessibleExpense(100L, approver))
                .thenThrow(new AccessDeniedException("Cannot access this expense"));

        assertThatThrownBy(() -> financeProcessingService.processExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot access this expense");

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Processing records the exact audit event contract")
    void processRecordsExactAuditEvent() {
        Expense expense = approvedExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canProcessExpense(approver, expense)).thenReturn(true);

        financeProcessingService.processExpense(100L, "approver");

        verify(auditLogService).record(AuditActions.EXPENSE_PROCESSED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense processed for payment", "approver");
    }

    private Expense approvedExpense() {
        Expense expense = pendingExpense();
        expense.setStatus(ExpenseStatus.APPROVED);
        return expense;
    }

    private Expense expenseWithStatus(ExpenseStatus status) {
        Expense expense = pendingExpense();
        expense.setStatus(status);
        return expense;
    }

    private Expense pendingExpense() {
        return Expense.builder()
                .id(100L)
                .status(ExpenseStatus.PENDING)
                .tenant(tenant)
                .department(department)
                .owner(otherUser)
                .build();
    }

    private User userWithUsername(String username) {
        return User.builder()
                .id((long) username.hashCode())
                
                .password("secret")
                .enabled(true)
                .accountNonLocked(true)
                .build();
    }
}
