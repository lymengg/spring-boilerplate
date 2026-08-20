package com.example.demo.constants;

public final class Authorities {

    // Tenant management
    public static final String TENANT_READ = "TENANT_READ";
    public static final String TENANT_CREATE = "TENANT_CREATE";
    public static final String TENANT_UPDATE = "TENANT_UPDATE";
    public static final String TENANT_DELETE = "TENANT_DELETE";

    // User management
    public static final String USER_READ = "USER_READ";
    public static final String USER_WRITE = "USER_WRITE";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_ENABLE = "USER_ENABLE";
    public static final String USER_ASSIGN_ROLE = "USER_ASSIGN_ROLE";

    // Role management
    public static final String ROLE_READ = "ROLE_READ";
    public static final String ROLE_WRITE = "ROLE_WRITE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String ROLE_ASSIGN_PERMISSION = "ROLE_ASSIGN_PERMISSION";

    // Department management
    public static final String DEPARTMENT_READ = "DEPARTMENT_READ";
    public static final String DEPARTMENT_CREATE = "DEPARTMENT_CREATE";
    public static final String DEPARTMENT_UPDATE = "DEPARTMENT_UPDATE";
    public static final String DEPARTMENT_DELETE = "DEPARTMENT_DELETE";

    // Expense management
    public static final String EXPENSE_READ = "EXPENSE_READ";
    public static final String EXPENSE_CREATE = "EXPENSE_CREATE";
    public static final String EXPENSE_UPDATE = "EXPENSE_UPDATE";
    public static final String EXPENSE_DELETE = "EXPENSE_DELETE";
    public static final String EXPENSE_APPROVE = "EXPENSE_APPROVE";
    public static final String EXPENSE_REJECT = "EXPENSE_REJECT";
    public static final String EXPENSE_PROCESS = "EXPENSE_PROCESS";

    // MFA management
    public static final String MFA_MANAGE = "MFA_MANAGE";

    // Reporting and audit
    public static final String REPORT_READ = "REPORT_READ";
    public static final String AUDIT_LOG_READ = "AUDIT_LOG_READ";

    private Authorities() {
    }
}
