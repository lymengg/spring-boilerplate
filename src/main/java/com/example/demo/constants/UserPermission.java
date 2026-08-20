package com.example.demo.constants;

public enum UserPermission {
    // Tenant management
    TENANT_READ,
    TENANT_CREATE,
    TENANT_UPDATE,
    TENANT_DELETE,

    // User management
    USER_READ,
    USER_WRITE,
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,
    USER_ENABLE,
    USER_ASSIGN_ROLE,

    // Role management
    ROLE_READ,
    ROLE_WRITE,
    ROLE_DELETE,
    ROLE_ASSIGN_PERMISSION,

    // Department management
    DEPARTMENT_READ,
    DEPARTMENT_CREATE,
    DEPARTMENT_UPDATE,
    DEPARTMENT_DELETE,

    // Expense management
    EXPENSE_READ,
    EXPENSE_READ_ALL,
    EXPENSE_CREATE,
    EXPENSE_UPDATE,
    EXPENSE_DELETE,
    EXPENSE_APPROVE,
    EXPENSE_REJECT,
    EXPENSE_PROCESS,

    // MFA management
    MFA_MANAGE,

    // Reporting and audit
    REPORT_READ,
    AUDIT_LOG_READ
}
