package com.example.demo.security.service;

import com.example.demo.constants.Authorities;
import com.example.demo.constants.Roles;
import com.example.demo.entity.Department;
import com.example.demo.entity.Expense;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationService {

    public boolean hasAuthority(User user, String authority) {
        return user != null && user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public boolean hasRole(User user, String role) {
        return hasAuthority(user, "ROLE_" + role);
    }

    public boolean isSuperAdmin(User user) {
        return user != null && user.getTenant() == null && hasRole(user, Roles.PLATFORM_ADMIN);
    }

    public boolean belongsToTenant(User user, Tenant tenant) {
        return user != null && user.getTenant() != null
                && tenant != null && user.getTenant().getId().equals(tenant.getId());
    }

    public boolean canAccessTenant(User user, Tenant tenant) {
        return isSuperAdmin(user) || belongsToTenant(user, tenant);
    }

    public boolean canManageTenant(User user, Tenant tenant) {
        return isSuperAdmin(user)
                || (belongsToTenant(user, tenant) && hasAuthority(user, Authorities.TENANT_UPDATE));
    }

    public boolean belongsToDepartment(User user, Department department) {
        return user != null && user.getDepartment() != null
                && department != null && user.getDepartment().getId().equals(department.getId());
    }

    public boolean isDepartmentManager(User user, Department department) {
        return user != null && department != null && department.getManager() != null
                && user.getId().equals(department.getManager().getId());
    }

    public boolean managesDepartment(User user, Department department) {
        return isSuperAdmin(user)
                || canManageTenant(user, department.getTenant())
                || isDepartmentManager(user, department);
    }

    public boolean canAccessDepartment(User user, Department department) {
        return isSuperAdmin(user)
                || canManageTenant(user, department.getTenant())
                || isDepartmentManager(user, department)
                || belongsToDepartment(user, department);
    }

    public boolean isResourceOwner(User user, Long resourceOwnerId) {
        return user != null && user.getId().equals(resourceOwnerId);
    }

    public boolean isResourceOwner(User user, User resourceOwner) {
        return user != null && resourceOwner != null && user.getId().equals(resourceOwner.getId());
    }

    public boolean canViewExpense(User user, Expense expense) {
        if (expense == null || user == null) {
            return false;
        }
        if (isSuperAdmin(user) || isResourceOwner(user, expense.getOwner())) {
            return true;
        }
        if (!canAccessTenant(user, expense.getTenant())) {
            return false;
        }
        if (hasAuthority(user, Authorities.EXPENSE_PROCESS) || hasAuthority(user, Authorities.AUDIT_LOG_READ)) {
            return true;
        }
        return (hasAuthority(user, Authorities.EXPENSE_APPROVE) || hasAuthority(user, Authorities.EXPENSE_REJECT))
                && (canManageTenant(user, expense.getTenant()) || isDepartmentManager(user, expense.getDepartment()));
    }

    public boolean canEditExpense(User user, Expense expense) {
        if (expense == null || user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }
        if (!canAccessTenant(user, expense.getTenant())) {
            return false;
        }
        return isResourceOwner(user, expense.getOwner())
                || (canManageTenant(user, expense.getTenant()) && hasAuthority(user, Authorities.EXPENSE_UPDATE));
    }

    public boolean canCancelExpense(User user, Expense expense) {
        return canEditExpense(user, expense);
    }

    public boolean canApproveExpense(User user, Expense expense) {
        if (expense == null || user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }
        return canAccessTenant(user, expense.getTenant())
                && hasAuthority(user, Authorities.EXPENSE_APPROVE)
                && (canManageTenant(user, expense.getTenant()) || isDepartmentManager(user, expense.getDepartment()));
    }

    public boolean canRejectExpense(User user, Expense expense) {
        if (expense == null || user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }
        return canAccessTenant(user, expense.getTenant())
                && hasAuthority(user, Authorities.EXPENSE_REJECT)
                && (canManageTenant(user, expense.getTenant()) || isDepartmentManager(user, expense.getDepartment()));
    }

    public boolean canProcessExpense(User user, Expense expense) {
        if (expense == null || user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }
        return canAccessTenant(user, expense.getTenant()) && hasAuthority(user, Authorities.EXPENSE_PROCESS);
    }
}
