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
import com.example.demo.service.impl.ApprovalServiceImpl;
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
class ApprovalServiceImplTest {

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
    private ApprovalServiceImpl approvalService;

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
    @DisplayName("Approving a pending expense sets APPROVED, decision date and approver, saves and audits")
    void approvePendingExpenseSetsApprovedState() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canApproveExpense(approver, expense)).thenReturn(true);

        ExpenseResponse result = approvalService.approveExpense(100L, "approver");

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseService).save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(saved.getDecisionDate()).isNotNull();
        assertThat(saved.getApprovedBy()).isEqualTo(approver);
        verify(auditLogService).record(AuditActions.EXPENSE_APPROVED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense approved", "approver");
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Rejecting a pending expense sets REJECTED, decision date and rejector, saves and audits")
    void rejectPendingExpenseSetsRejectedState() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canRejectExpense(approver, expense)).thenReturn(true);

        ExpenseResponse result = approvalService.rejectExpense(100L, "approver");

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseService).save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ExpenseStatus.REJECTED);
        assertThat(saved.getDecisionDate()).isNotNull();
        assertThat(saved.getRejectedBy()).isEqualTo(approver);
        verify(auditLogService).record(AuditActions.EXPENSE_REJECTED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense rejected", "approver");
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Approving an already approved expense throws IllegalStateException")
    void approveApprovedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.APPROVED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be approved");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
    }

    @Test
    @DisplayName("Approving a rejected expense throws IllegalStateException")
    void approveRejectedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.REJECTED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be approved");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.REJECTED);
    }

    @Test
    @DisplayName("Approving a cancelled expense throws IllegalStateException")
    void approveCancelledExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.CANCELLED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be approved");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.CANCELLED);
    }

    @Test
    @DisplayName("Approving a processed expense throws IllegalStateException")
    void approveProcessedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.PROCESSED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be approved");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.PROCESSED);
    }

    @Test
    @DisplayName("Rejecting an approved expense throws IllegalStateException")
    void rejectApprovedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.APPROVED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be rejected");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
    }

    @Test
    @DisplayName("Rejecting an already rejected expense throws IllegalStateException (idempotency guard)")
    void rejectRejectedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.REJECTED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be rejected");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.REJECTED);
    }

    @Test
    @DisplayName("Rejecting a cancelled expense throws IllegalStateException")
    void rejectCancelledExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.CANCELLED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be rejected");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.CANCELLED);
    }

    @Test
    @DisplayName("Rejecting a processed expense throws IllegalStateException")
    void rejectProcessedExpenseFails() {
        Expense expense = expenseWithStatus(ExpenseStatus.PROCESSED);
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending expenses can be rejected");
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.PROCESSED);
    }

    @Test
    @DisplayName("Failed transitions never persist, audit or mutate the expense")
    void failedTransitionsHaveNoSideEffects() {
        for (ExpenseStatus status : List.of(ExpenseStatus.APPROVED, ExpenseStatus.REJECTED,
                ExpenseStatus.CANCELLED, ExpenseStatus.PROCESSED)) {
            Expense expense = expenseWithStatus(status);
            when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);

            assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(expense.getStatus()).isEqualTo(status);
        }
        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Approving without EXPENSE_APPROVE authority throws AccessDeniedException")
    void approveWithoutAuthorityDenied() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canApproveExpense(approver, expense)).thenReturn(false);

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot approve this expense");

        verify(authorizationService).canApproveExpense(approver, expense);
        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Approving with authority but not manager or tenant-admin throws AccessDeniedException")
    void approveWithAuthorityButNotManagerOrTenantAdminDenied() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canApproveExpense(approver, expense)).thenReturn(false);

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot approve this expense");

        verify(authorizationService).canApproveExpense(approver, expense);
        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Rejecting without EXPENSE_REJECT authority throws AccessDeniedException")
    void rejectWithoutAuthorityDenied() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canRejectExpense(approver, expense)).thenReturn(false);

        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot reject this expense");

        verify(authorizationService).canRejectExpense(approver, expense);
        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Rejecting with authority but not manager or tenant-admin throws AccessDeniedException")
    void rejectWithAuthorityButNotManagerOrTenantAdminDenied() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canRejectExpense(approver, expense)).thenReturn(false);

        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot reject this expense");

        verify(authorizationService).canRejectExpense(approver, expense);
        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Super admin can approve an expense (bypasses manager checks)")
    void superAdminCanApproveExpense() {
        approver.setTenant(null);
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canApproveExpense(approver, expense)).thenReturn(true);

        ExpenseResponse result = approvalService.approveExpense(100L, "approver");

        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(expense.getApprovedBy()).isEqualTo(approver);
        verify(expenseService).save(expense);
        verify(auditLogService).record(AuditActions.EXPENSE_APPROVED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense approved", "approver");
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Authorization failures never persist or audit the expense")
    void authorizationFailureHasNoSideEffects() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canApproveExpense(approver, expense)).thenReturn(false);
        when(authorizationService.canRejectExpense(approver, expense)).thenReturn(false);

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class);

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.PENDING);
    }

    @Test
    @DisplayName("Approving a nonexistent expense propagates IllegalArgumentException")
    void approveNonexistentExpenseFails() {
        when(expenseService.findAccessibleExpense(100L, approver))
                .thenThrow(new IllegalArgumentException("Expense not found"));

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expense not found");

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Rejecting a nonexistent expense propagates IllegalArgumentException")
    void rejectNonexistentExpenseFails() {
        when(expenseService.findAccessibleExpense(100L, approver))
                .thenThrow(new IllegalArgumentException("Expense not found"));

        assertThatThrownBy(() -> approvalService.rejectExpense(100L, "approver"))
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

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "crossTenantUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expense not found");

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("User with no tenant gets AccessDeniedException when approving")
    void userWithoutTenantCannotApprove() {
        approver.setTenant(null);
        when(expenseService.findAccessibleExpense(100L, approver))
                .thenThrow(new AccessDeniedException("Cannot access this expense"));

        assertThatThrownBy(() -> approvalService.approveExpense(100L, "approver"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot access this expense");

        verify(expenseService, never()).save(any(Expense.class));
        verify(auditLogService, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Approving records the exact audit event contract")
    void approveRecordsExactAuditEvent() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canApproveExpense(approver, expense)).thenReturn(true);

        approvalService.approveExpense(100L, "approver");

        verify(auditLogService).record(AuditActions.EXPENSE_APPROVED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense approved", "approver");
    }

    @Test
    @DisplayName("Rejecting records the exact audit event contract")
    void rejectRecordsExactAuditEvent() {
        Expense expense = pendingExpense();
        when(expenseService.findAccessibleExpense(100L, approver)).thenReturn(expense);
        when(authorizationService.canRejectExpense(approver, expense)).thenReturn(true);

        approvalService.rejectExpense(100L, "approver");

        verify(auditLogService).record(AuditActions.EXPENSE_REJECTED, AuditActions.RESOURCE_EXPENSE,
                "100", "Expense rejected", "approver");
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

    private Expense expenseWithStatus(ExpenseStatus status) {
        Expense expense = pendingExpense();
        expense.setStatus(status);
        return expense;
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
