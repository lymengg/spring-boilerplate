package com.example.demo.constants;

public final class AuditActions {

    // User management
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_ENABLED = "USER_ENABLED";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String USER_ROLE_ASSIGNED = "USER_ROLE_ASSIGNED";
    public static final String USER_ROLE_REMOVED = "USER_ROLE_REMOVED";
    public static final String USER_MFA_ENABLED = "USER_MFA_ENABLED";
    public static final String USER_MFA_DISABLED = "USER_MFA_DISABLED";
    public static final String USER_MFA_RESET = "USER_MFA_RESET";

    // Expense management
    public static final String EXPENSE_CREATED = "EXPENSE_CREATED";
    public static final String EXPENSE_UPDATED = "EXPENSE_UPDATED";
    public static final String EXPENSE_CANCELLED = "EXPENSE_CANCELLED";
    public static final String EXPENSE_APPROVED = "EXPENSE_APPROVED";
    public static final String EXPENSE_REJECTED = "EXPENSE_REJECTED";
    public static final String EXPENSE_PROCESSED = "EXPENSE_PROCESSED";

    // Resource types
    public static final String RESOURCE_USER = "USER";
    public static final String RESOURCE_EXPENSE = "EXPENSE";

    private AuditActions() {
    }
}
