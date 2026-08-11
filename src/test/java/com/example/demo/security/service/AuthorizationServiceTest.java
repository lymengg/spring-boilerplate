package com.example.demo.security.service;

import com.example.demo.constants.Authorities;
import com.example.demo.constants.Roles;
import com.example.demo.constants.UserPermission;
import com.example.demo.entity.Department;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServiceTest {

    private AuthorizationService authorizationService;

    private Tenant tenant;
    private Department department;
    private Role employeeRole;
    private Role managerRole;
    private Role tenantAdminRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService();

        tenant = Tenant.builder().id(1L).name("Tenant").build();
        department = Department.builder().id(10L).name("Dept").tenant(tenant).build();

        employeeRole = Role.builder().name(Roles.EMPLOYEE).permissions(new HashSet<>()).build();
        employeeRole.getPermissions().add(UserPermission.EXPENSE_READ);
        employeeRole.getPermissions().add(UserPermission.EXPENSE_CREATE);
        employeeRole.getPermissions().add(UserPermission.EXPENSE_UPDATE);
        employeeRole.getPermissions().add(UserPermission.EXPENSE_DELETE);

        managerRole = Role.builder().name(Roles.MANAGER).permissions(new HashSet<>()).build();
        managerRole.getPermissions().add(UserPermission.EXPENSE_READ);
        managerRole.getPermissions().add(UserPermission.EXPENSE_APPROVE);
        managerRole.getPermissions().add(UserPermission.EXPENSE_REJECT);
        managerRole.getPermissions().add(UserPermission.REPORT_READ);

        tenantAdminRole = Role.builder().name("TENANT_ADMIN").permissions(new HashSet<>()).build();
        tenantAdminRole.getPermissions().add(UserPermission.TENANT_UPDATE);
        tenantAdminRole.getPermissions().add(UserPermission.EXPENSE_UPDATE);
        tenantAdminRole.getPermissions().add(UserPermission.EXPENSE_APPROVE);

        adminRole = Role.builder().name(Roles.ADMIN).permissions(new HashSet<>()).build();
        for (UserPermission permission : UserPermission.values()) {
            adminRole.getPermissions().add(permission);
        }
    }

    @Test
    @DisplayName("Super admin can manage any tenant without belonging to it")
    void superAdminCanManageAnyTenant() {
        User admin = userWithRole(adminRole, null, null);
        assertThat(authorizationService.canManageTenant(admin, tenant)).isTrue();
    }

    @Test
    @DisplayName("Tenant member without TENANT_UPDATE cannot manage tenant")
    void tenantMemberCannotManageTenantWithoutAuthority() {
        User employee = userWithRole(employeeRole, tenant, department);
        assertThat(authorizationService.canManageTenant(employee, tenant)).isFalse();
    }

    @Test
    @DisplayName("Tenant admin can manage their own tenant")
    void tenantAdminCanManageOwnTenant() {
        User tenantAdmin = userWithRole(tenantAdminRole, tenant, department);
        assertThat(authorizationService.canManageTenant(tenantAdmin, tenant)).isTrue();
    }

    @Test
    @DisplayName("Employee can edit their own expense")
    void employeeCanEditOwnExpense() {
        User employee = userWithRole(employeeRole, tenant, department);
        Expense expense = expenseOwnedBy(employee);
        assertThat(authorizationService.canEditExpense(employee, expense)).isTrue();
    }

    @Test
    @DisplayName("Employee cannot edit another employee's expense")
    void employeeCannotEditOtherExpense() {
        User employee = userWithRole(employeeRole, tenant, department);
        User other = userWithRole(employeeRole, tenant, department);
        Expense expense = expenseOwnedBy(other);
        assertThat(authorizationService.canEditExpense(employee, expense)).isFalse();
    }

    @Test
    @DisplayName("Tenant admin can edit any expense in their tenant")
    void tenantAdminCanEditAnyTenantExpense() {
        User tenantAdmin = userWithRole(tenantAdminRole, tenant, department);
        User other = userWithRole(employeeRole, tenant, department);
        Expense expense = expenseOwnedBy(other);
        assertThat(authorizationService.canEditExpense(tenantAdmin, expense)).isTrue();
    }

    @Test
    @DisplayName("Manager can approve expense in their managed department")
    void managerCanApproveDepartmentExpense() {
        User manager = userWithRole(managerRole, tenant, department);
        department.setManager(manager);
        User employee = userWithRole(employeeRole, tenant, department);
        Expense expense = expenseOwnedBy(employee);
        assertThat(authorizationService.canApproveExpense(manager, expense)).isTrue();
    }

    @Test
    @DisplayName("Manager cannot approve expense in another department")
    void managerCannotApproveOtherDepartmentExpense() {
        User manager = userWithRole(managerRole, tenant, department);
        department.setManager(manager);
        Department otherDepartment = Department.builder().id(11L).name("Other Dept").tenant(tenant).build();
        User otherEmployee = userWithRole(employeeRole, tenant, otherDepartment);
        Expense expense = expenseOwnedBy(otherEmployee);
        assertThat(authorizationService.canApproveExpense(manager, expense)).isFalse();
    }

    @Test
    @DisplayName("Tenant admin can approve any expense in their tenant")
    void tenantAdminCanApproveAnyTenantExpense() {
        User tenantAdmin = userWithRole(tenantAdminRole, tenant, department);
        Department otherDepartment = Department.builder().id(11L).name("Other Dept").tenant(tenant).build();
        User otherEmployee = userWithRole(employeeRole, tenant, otherDepartment);
        Expense expense = expenseOwnedBy(otherEmployee);
        assertThat(authorizationService.canApproveExpense(tenantAdmin, expense)).isTrue();
    }

    private long nextId = 1;

    private User userWithRole(Role role, Tenant userTenant, Department userDepartment) {
        User user = User.builder()
                .id(nextId++)
                .username(role.getName().toLowerCase() + nextId)
                .tenant(userTenant)
                .department(userDepartment)
                .build();
        user.getRoles().add(role);
        return user;
    }

    private Expense expenseOwnedBy(User owner) {
        return Expense.builder()
                .id(1L)
                .title("Travel")
                .status(ExpenseStatus.PENDING)
                .owner(owner)
                .department(owner.getDepartment())
                .tenant(owner.getTenant())
                .build();
    }
}
