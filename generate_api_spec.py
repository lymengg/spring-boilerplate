#!/usr/bin/env python3
"""
API Specification PDF Generator
Generates a professional corporate-ready API Specification document
using ReportLab's Platypus framework.
"""

from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, HRFlowable
)

PRIMARY_ACCENT = HexColor("#1A365D")
SECONDARY_ACCENT = HexColor("#0D9488")
NEUTRAL_DARK = HexColor("#1F2937")
NEUTRAL_LIGHT = HexColor("#F3F4F6")
WHITE = HexColor("#FFFFFF")
LIGHT_BORDER = HexColor("#D1D5DB")
PAGE_WIDTH, PAGE_HEIGHT = letter
MARGIN = 54
CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

ENDPOINT_GROUPS = [
    "Authentication",
    "Expenses",
    "User Management",
    "Role Management",
    "Tenant Management",
    "Department Management",
    "Audit Log",
    "System / Actuator",
]

ENDPOINTS = [
    {"group": "Authentication", "method": "POST", "path": "/api/auth/login", "summary": "Authenticate a user and issue JWT tokens", "auth": "None (Public)", "description": "Validates credentials and returns an access token. If multi-factor authentication is enabled for the user, returns an MFA session token instead. The refresh token is set as an HttpOnly Secure cookie.", "request_body": [("usernameOrEmail", "string", "Required. The username or email address.", "NotBlank"), ("password", "string", "Required. The user password.", "NotBlank, Size(1-100)"), ("rememberMe", "boolean", "Optional. Extends refresh token lifetime.", "None")], "response": "TokenResponse or MfaLoginResponse", "response_fields": [("accessToken", "string", "The JWT access token."), ("tokenType", "string", "Always 'Bearer'."), ("expiresIn", "long", "Token lifetime in seconds."), ("username", "string", "Authenticated username."), ("roles", "string[]", "Granted role names.")], "status_codes": ["200 OK", "401 Unauthorized", "429 Too Many Requests (account locked)"]},
    {"group": "Authentication", "method": "POST", "path": "/api/auth/mfa/verify", "summary": "Verify multi-factor authentication code", "auth": "None (Public)", "description": "Completes the MFA challenge using the session token and a 6-digit code. Returns JWT tokens on success.", "request_body": [("mfaSessionToken", "string", "Required. Session token from login.", "NotBlank"), ("code", "string", "Required. 6-digit verification code.", "NotBlank, Size(6,6)")], "response": "TokenResponse", "response_fields": [("accessToken", "string", "The JWT access token."), ("tokenType", "string", "Always 'Bearer'."), ("expiresIn", "long", "Token lifetime in seconds."), ("username", "string", "Authenticated username."), ("roles", "string[]", "Granted role names.")], "status_codes": ["200 OK", "401 Unauthorized"]},
    {"group": "Authentication", "method": "POST", "path": "/api/auth/refresh", "summary": "Refresh an expired access token", "auth": "None (Public - uses HttpOnly cookie)", "description": "Rotates the refresh token stored in the HttpOnly cookie and issues a new access token. The previous refresh token is invalidated.", "request_body": [], "response": "TokenResponse", "response_fields": [("accessToken", "string", "New JWT access token."), ("tokenType", "string", "Always 'Bearer'."), ("expiresIn", "long", "Token lifetime in seconds."), ("username", "string", "Authenticated username."), ("roles", "string[]", "Granted role names.")], "status_codes": ["200 OK", "401 Unauthorized (invalid/expired refresh token)"]},
    {"group": "Authentication", "method": "POST", "path": "/api/auth/logout", "summary": "Invalidate the current session", "auth": "Authenticated (JWT required)", "description": "Blacklists the current access token, clears the refresh token cookie, and records a security audit event.", "request_body": [], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "401 Unauthorized"]},
    {"group": "Authentication", "method": "GET", "path": "/api/auth/me", "summary": "Retrieve the current user profile", "auth": "Authenticated (JWT required)", "description": "Returns the profile details of the currently authenticated user including roles and MFA status.", "request_body": [], "response": "UserProfileResponse", "response_fields": [("username", "string", "The username."), ("email", "string", "The email address."), ("firstName", "string", "First name."), ("lastName", "string", "Last name."), ("roles", "string[]", "Assigned role names."), ("enabled", "boolean", "Account enabled status."), ("mfaEnabled", "boolean", "Whether MFA is active."), ("mfaMethod", "string", "MFA method (NONE, TOTP, EMAIL).")], "status_codes": ["200 OK", "401 Unauthorized"]},
    {"group": "Authentication", "method": "POST", "path": "/api/auth/change-password", "summary": "Change the current user password", "auth": "Authenticated (JWT required)", "description": "Validates the current password, sets the new password, and clears all refresh tokens for the user.", "request_body": [("currentPassword", "string", "Required. The existing password.", "NotBlank"), ("newPassword", "string", "Required. The new password.", "NotBlank, @Password"), ("confirmPassword", "string", "Required. Must match newPassword.", "NotBlank")], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized"]},
    {"group": "Authentication", "method": "POST", "path": "/api/auth/forgot-password", "summary": "Request a password reset link", "auth": "None (Public)", "description": "Sends a password reset email if the provided email address exists in the system. Returns success regardless to prevent email enumeration.", "request_body": [("email", "string", "Required. The account email address.", "NotBlank, Email")], "response": "Void", "response_fields": [], "status_codes": ["200 OK"]},
    {"group": "Authentication", "method": "POST", "path": "/api/auth/reset-password", "summary": "Reset password using a token", "auth": "None (Public)", "description": "Resets the user password using a single-use token received via email. The token has a configurable time-to-live.", "request_body": [("token", "string", "Required. The password reset token.", "NotBlank"), ("newPassword", "string", "Required. The new password.", "NotBlank, @Password"), ("confirmPassword", "string", "Required. Must match newPassword.", "NotBlank")], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "400 Bad Request (invalid/expired token)"]},
    {"group": "Expenses", "method": "GET", "path": "/api/expenses", "summary": "List expenses with optional filters", "auth": "Authenticated - requires EXPENSE_READ", "description": "Returns a paginated list of expenses. Tenant-scoped: regular users see only their tenant expenses; super admin sees all. Supports filtering by status, tenant, and department.", "request_body": [], "query_params": [("status", "string", "Optional. Filter by status: PENDING, APPROVED, REJECTED, CANCELLED, PROCESSED."), ("tenantId", "long", "Optional. Filter by tenant (super admin only)."), ("departmentId", "long", "Optional. Filter by department."), ("page", "int", "Optional. Page number (0-based). Default: 0."), ("size", "int", "Optional. Page size. Default: 20."), ("sort", "string", "Optional. Sort field and direction.")], "response": "Page of ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("title", "string", "Expense title."), ("description", "string", "Expense description."), ("amount", "decimal", "Expense amount."), ("category", "string", "Expense category."), ("status", "string", "Current status."), ("submissionDate", "datetime", "When the expense was submitted."), ("ownerUsername", "string", "Username of the expense owner."), ("departmentName", "string", "Department name."), ("tenantName", "string", "Tenant name.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Expenses", "method": "GET", "path": "/api/expenses/{id}", "summary": "Retrieve a single expense by ID", "auth": "Authenticated - requires EXPENSE_READ", "description": "Returns the full details of a specific expense. Includes additional ownership and tenant access checks.", "request_body": [], "path_params": [("id", "long", "Required. The expense ID.")], "response": "ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("title", "string", "Expense title."), ("description", "string", "Expense description."), ("amount", "decimal", "Expense amount."), ("category", "string", "Expense category."), ("status", "string", "Current status."), ("submissionDate", "datetime", "When the expense was submitted."), ("decisionDate", "datetime", "When approved or rejected."), ("processedDate", "datetime", "When processed by finance."), ("ownerUsername", "string", "Username of the owner."), ("approvedByUsername", "string", "Username of the approver."), ("rejectedByUsername", "string", "Username of the rejector."), ("processedByUsername", "string", "Username of the processor.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "Expenses", "method": "POST", "path": "/api/expenses", "summary": "Submit a new expense", "auth": "Authenticated - requires EXPENSE_CREATE", "description": "Creates a new expense in PENDING status. The authenticated user becomes the owner. Tenant membership is required.", "request_body": [("title", "string", "Required. Expense title.", "NotBlank, Size(max=200)"), ("description", "string", "Optional. Expense description.", "Size(max=1000)"), ("amount", "decimal", "Required. Positive amount.", "NotNull, Positive, Digits(12,4)"), ("category", "string", "Required. Expense category.", "NotBlank, Size(max=50)"), ("departmentId", "long", "Optional. Associated department ID.", "None")], "response": "ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("title", "string", "Expense title."), ("amount", "decimal", "Expense amount."), ("status", "string", "Always PENDING on creation."), ("submissionDate", "datetime", "Creation timestamp.")], "status_codes": ["201 Created", "400 Bad Request", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Expenses", "method": "PUT", "path": "/api/expenses/{id}", "summary": "Update an existing expense", "auth": "Authenticated - requires EXPENSE_UPDATE", "description": "Updates the title, description, amount, or category of an expense. Only PENDING expenses can be modified. Ownership check is enforced.", "request_body": [("title", "string", "Required. Expense title.", "NotBlank, Size(max=200)"), ("description", "string", "Optional. Expense description.", "Size(max=1000)"), ("amount", "decimal", "Required. Positive amount.", "NotNull, Positive, Digits(12,4)"), ("category", "string", "Required. Expense category.", "NotBlank, Size(max=50)")], "path_params": [("id", "long", "Required. The expense ID.")], "response": "ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("title", "string", "Updated title."), ("amount", "decimal", "Updated amount."), ("status", "string", "Must be PENDING.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Expenses", "method": "POST", "path": "/api/expenses/{id}/cancel", "summary": "Cancel a pending expense", "auth": "Authenticated - requires EXPENSE_UPDATE", "description": "Sets the expense status to CANCELLED. Only the owner of a PENDING expense may cancel it.", "request_body": [], "path_params": [("id", "long", "Required. The expense ID.")], "response": "ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("status", "string", "Updated to CANCELLED.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Expenses", "method": "POST", "path": "/api/expenses/{id}/approve", "summary": "Approve a pending expense", "auth": "Authenticated - requires EXPENSE_APPROVE", "description": "Approves a PENDING expense. The approver must have appropriate authority and cannot approve their own expense.", "request_body": [], "path_params": [("id", "long", "Required. The expense ID.")], "response": "ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("status", "string", "Updated to APPROVED."), ("approvedByUsername", "string", "Username of the approver."), ("decisionDate", "datetime", "Approval timestamp.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Expenses", "method": "POST", "path": "/api/expenses/{id}/reject", "summary": "Reject a pending expense", "auth": "Authenticated - requires EXPENSE_REJECT", "description": "Rejects a PENDING expense. The rejector must have appropriate authority.", "request_body": [], "path_params": [("id", "long", "Required. The expense ID.")], "response": "ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("status", "string", "Updated to REJECTED."), ("rejectedByUsername", "string", "Username of the rejector."), ("decisionDate", "datetime", "Rejection timestamp.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Expenses", "method": "POST", "path": "/api/expenses/{id}/process", "summary": "Process an approved expense", "auth": "Authenticated - requires EXPENSE_PROCESS", "description": "Marks an APPROVED expense as PROCESSED by the finance team. Only APPROVED expenses can be processed.", "request_body": [], "path_params": [("id", "long", "Required. The expense ID.")], "response": "ExpenseResponse", "response_fields": [("id", "long", "Unique expense identifier."), ("status", "string", "Updated to PROCESSED."), ("processedByUsername", "string", "Username of the processor."), ("processedDate", "datetime", "Processing timestamp.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "User Management", "method": "POST", "path": "/api/management/users", "summary": "Create a new user", "auth": "Authenticated - requires USER_CREATE", "description": "Creates a new user account with the specified role and department. Duplicate username or email values are rejected.", "request_body": [("username", "string", "Required. Unique username.", "NotBlank, Size(3-50)"), ("email", "string", "Required. Unique email.", "NotBlank, Email, Size(max=100)"), ("password", "string", "Required. User password.", "NotBlank, @Password"), ("firstName", "string", "Optional. First name.", "Size(max=50)"), ("lastName", "string", "Optional. Last name.", "Size(max=50)"), ("roleName", "string", "Optional. Role name to assign.", "Size(max=50)"), ("tenantId", "long", "Optional. Tenant ID.", "None"), ("departmentId", "long", "Required. Department ID.", "NotNull")], "response": "UserResponse", "response_fields": [("id", "long", "Unique user identifier."), ("username", "string", "The username."), ("email", "string", "The email address."), ("firstName", "string", "First name."), ("lastName", "string", "Last name."), ("enabled", "boolean", "Account enabled status."), ("departmentName", "string", "Assigned department name."), ("roles", "string[]", "Assigned role names."), ("permissions", "string[]", "Effective permission names."), ("mfaEnabled", "boolean", "MFA status."), ("createdAt", "datetime", "Account creation timestamp.")], "status_codes": ["201 Created", "400 Bad Request", "401 Unauthorized", "403 Forbidden"]},
    {"group": "User Management", "method": "GET", "path": "/api/management/users", "summary": "List users with pagination", "auth": "Authenticated - requires USER_READ", "description": "Returns a paginated list of users. Super admin sees all users; tenant admin sees only users within their tenant.", "request_body": [], "query_params": [("page", "int", "Optional. Page number (0-based). Default: 0."), ("size", "int", "Optional. Page size. Default: 20."), ("sort", "string", "Optional. Sort field and direction.")], "response": "Page of UserResponse", "response_fields": [("id", "long", "Unique user identifier."), ("username", "string", "The username."), ("email", "string", "The email address."), ("enabled", "boolean", "Account enabled status."), ("departmentName", "string", "Assigned department name."), ("roles", "string[]", "Assigned role names.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden"]},
    {"group": "User Management", "method": "GET", "path": "/api/management/users/{id}", "summary": "Retrieve a user by ID", "auth": "Authenticated - requires USER_READ", "description": "Returns the full details of a specific user. Tenant-scoped access is enforced.", "request_body": [], "path_params": [("id", "long", "Required. The user ID.")], "response": "UserResponse", "response_fields": [("id", "long", "Unique user identifier."), ("username", "string", "The username."), ("email", "string", "The email address."), ("firstName", "string", "First name."), ("lastName", "string", "Last name."), ("enabled", "boolean", "Account enabled status."), ("accountNonLocked", "boolean", "Account lock status."), ("departmentName", "string", "Assigned department name."), ("roles", "string[]", "Assigned role names."), ("permissions", "string[]", "Effective permission names."), ("mfaEnabled", "boolean", "MFA status."), ("mfaMethod", "string", "MFA method (NONE, TOTP, EMAIL)."), ("createdAt", "datetime", "Account creation timestamp."), ("updatedAt", "datetime", "Last update timestamp.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "User Management", "method": "PUT", "path": "/api/management/users/{id}", "summary": "Update a user profile", "auth": "Authenticated - requires USER_WRITE", "description": "Updates the first name, last name, or department of a user. Privilege hierarchy is enforced: a user cannot modify someone with equal or higher privileges.", "request_body": [("firstName", "string", "Optional. First name.", "Size(max=50)"), ("lastName", "string", "Optional. Last name.", "Size(max=50)"), ("departmentId", "long", "Optional. New department ID.", "None")], "path_params": [("id", "long", "Required. The user ID.")], "response": "UserResponse", "response_fields": [("id", "long", "Unique user identifier."), ("firstName", "string", "Updated first name."), ("lastName", "string", "Updated last name."), ("departmentName", "string", "Updated department name.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "User Management", "method": "DELETE", "path": "/api/management/users/{id}", "summary": "Delete a user", "auth": "Authenticated - requires USER_DELETE", "description": "Permanently removes a user account. Cannot delete yourself, cannot delete the last admin in a tenant, and privilege hierarchy is enforced.", "request_body": [], "path_params": [("id", "long", "Required. The user ID.")], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found", "409 Conflict"]},
    {"group": "User Management", "method": "POST", "path": "/api/management/users/{id}/enable", "summary": "Enable or disable a user account", "auth": "Authenticated - requires USER_ENABLE", "description": "Toggles the enabled status of a user account. Cannot change your own status, and cannot disable the last admin in a tenant.", "request_body": [("enabled", "boolean", "Required. True to enable, false to disable.", "NotNull")], "path_params": [("id", "long", "Required. The user ID.")], "response": "UserResponse", "response_fields": [("id", "long", "Unique user identifier."), ("enabled", "boolean", "Updated enabled status.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found", "409 Conflict"]},
    {"group": "User Management", "method": "POST", "path": "/api/management/users/{id}/roles", "summary": "Assign a role to a user", "auth": "Authenticated - requires USER_ASSIGN_ROLE", "description": "Assigns the specified role to a user. Role permission validation and privilege hierarchy checks are enforced.", "request_body": [("roleName", "string", "Required. The role name to assign.", "NotBlank")], "path_params": [("id", "long", "Required. The user ID.")], "response": "UserResponse", "response_fields": [("id", "long", "Unique user identifier."), ("roles", "string[]", "Updated role names."), ("permissions", "string[]", "Updated effective permissions.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "User Management", "method": "DELETE", "path": "/api/management/users/{id}/roles", "summary": "Remove a role from a user", "auth": "Authenticated - requires USER_ASSIGN_ROLE", "description": "Removes the specified role from a user. Cannot remove the last admin role, and permission hierarchy is enforced.", "request_body": [("roleName", "string", "Required. The role name to remove.", "NotBlank")], "path_params": [("id", "long", "Required. The user ID.")], "response": "UserResponse", "response_fields": [("id", "long", "Unique user identifier."), ("roles", "string[]", "Updated role names."), ("permissions", "string[]", "Updated effective permissions.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "404 Not Found", "409 Conflict"]},
    {"group": "User Management", "method": "POST", "path": "/api/management/users/{id}/mfa/enable", "summary": "Enable multi-factor authentication for a user", "auth": "Authenticated - requires USER_WRITE", "description": "Enables MFA for the specified user using the chosen method (TOTP or EMAIL). Returns setup details including QR URI for authenticator apps.", "request_body": [("method", "string", "Required. MFA method: TOTP or EMAIL.", "NotNull")], "path_params": [("id", "long", "Required. The user ID.")], "response": "MfaSetupResponse", "response_fields": [("qrUri", "string", "QR code URI for authenticator apps."), ("secret", "string", "MFA secret key."), ("method", "string", "Configured MFA method.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "User Management", "method": "POST", "path": "/api/management/users/{id}/mfa/disable", "summary": "Disable multi-factor authentication", "auth": "Authenticated - requires USER_WRITE", "description": "Disables MFA for the specified user. Privilege hierarchy is enforced.", "request_body": [], "path_params": [("id", "long", "Required. The user ID.")], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "User Management", "method": "POST", "path": "/api/management/users/{id}/mfa/reset", "summary": "Reset MFA configuration for a user", "auth": "Authenticated - requires USER_WRITE", "description": "Resets the existing MFA configuration and sets up a new one with the specified method. Returns new setup details.", "request_body": [("method", "string", "Required. New MFA method: TOTP or EMAIL.", "NotNull")], "path_params": [("id", "long", "Required. The user ID.")], "response": "MfaSetupResponse", "response_fields": [("qrUri", "string", "New QR code URI."), ("secret", "string", "New MFA secret key."), ("method", "string", "Configured MFA method.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "Role Management", "method": "GET", "path": "/api/management/roles", "summary": "List all roles", "auth": "Authenticated - requires ROLE_READ", "description": "Returns a paginated list of all roles in the system including their assigned permissions.", "request_body": [], "query_params": [("page", "int", "Optional. Page number (0-based). Default: 0."), ("size", "int", "Optional. Page size. Default: 20."), ("sort", "string", "Optional. Sort field and direction.")], "response": "Page of RoleResponse", "response_fields": [("id", "long", "Unique role identifier."), ("name", "string", "Role name (e.g., EMPLOYEE)."), ("title", "string", "Human-readable role title."), ("description", "string", "Role description."), ("permissions", "string[]", "Assigned permission names.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Role Management", "method": "GET", "path": "/api/management/roles/{id}", "summary": "Retrieve a role by ID", "auth": "Authenticated - requires ROLE_READ", "description": "Returns the full details of a specific role including all assigned permissions.", "request_body": [], "path_params": [("id", "long", "Required. The role ID.")], "response": "RoleResponse", "response_fields": [("id", "long", "Unique role identifier."), ("name", "string", "Role name."), ("title", "string", "Human-readable title."), ("description", "string", "Role description."), ("permissions", "string[]", "Assigned permission names.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "Role Management", "method": "POST", "path": "/api/management/roles", "summary": "Create a new role", "auth": "Authenticated - requires PLATFORM_ADMIN role", "description": "Creates a new custom role. Only platform administrators can create roles. Built-in roles cannot be duplicated.", "request_body": [("name", "string", "Required. Unique role name.", "NotBlank, Size(max=50)"), ("title", "string", "Optional. Human-readable title.", "Size(max=100)"), ("description", "string", "Optional. Role description.", "Size(max=255)")], "response": "RoleResponse", "response_fields": [("id", "long", "Unique role identifier."), ("name", "string", "Created role name."), ("title", "string", "Human-readable title."), ("description", "string", "Role description.")], "status_codes": ["201 Created", "400 Bad Request", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Role Management", "method": "PUT", "path": "/api/management/roles/{id}", "summary": "Update a role", "auth": "Authenticated - requires PLATFORM_ADMIN role", "description": "Updates the name, title, or description of a role. Built-in roles cannot be modified.", "request_body": [("name", "string", "Required. Updated role name.", "NotBlank, Size(max=50)"), ("title", "string", "Optional. Updated title.", "Size(max=100)"), ("description", "string", "Optional. Updated description.", "Size(max=255)")], "path_params": [("id", "long", "Required. The role ID.")], "response": "RoleResponse", "response_fields": [("id", "long", "Unique role identifier."), ("name", "string", "Updated role name."), ("title", "string", "Updated title."), ("description", "string", "Updated description.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Role Management", "method": "DELETE", "path": "/api/management/roles/{id}", "summary": "Delete a role", "auth": "Authenticated - requires PLATFORM_ADMIN role", "description": "Permanently removes a role. Built-in roles and roles currently assigned to users cannot be deleted.", "request_body": [], "path_params": [("id", "long", "Required. The role ID.")], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found", "409 Conflict"]},
    {"group": "Role Management", "method": "POST", "path": "/api/management/roles/{id}/permissions", "summary": "Add a permission to a role", "auth": "Authenticated - requires PLATFORM_ADMIN role", "description": "Adds a permission to the specified role. Built-in role permissions cannot be modified.", "request_body": [("permission", "string", "Required. Permission name from UserPermission enum.", "NotNull")], "path_params": [("id", "long", "Required. The role ID.")], "response": "RoleResponse", "response_fields": [("id", "long", "Unique role identifier."), ("permissions", "string[]", "Updated permission set.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Role Management", "method": "DELETE", "path": "/api/management/roles/{id}/permissions", "summary": "Remove a permission from a role", "auth": "Authenticated - requires PLATFORM_ADMIN role", "description": "Removes a permission from the specified role. Built-in role permissions cannot be modified.", "request_body": [("permission", "string", "Required. Permission name to remove.", "NotNull")], "path_params": [("id", "long", "Required. The role ID.")], "response": "RoleResponse", "response_fields": [("id", "long", "Unique role identifier."), ("permissions", "string[]", "Updated permission set.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Tenant Management", "method": "GET", "path": "/api/management/tenants", "summary": "List all tenants", "auth": "Authenticated - requires TENANT_READ", "description": "Returns a paginated list of tenants. Only platform administrators can list tenants. Supports optional name filtering.", "request_body": [], "query_params": [("name", "string", "Optional. Filter by tenant name."), ("page", "int", "Optional. Page number (0-based). Default: 0."), ("size", "int", "Optional. Page size. Default: 20."), ("sort", "string", "Optional. Sort field and direction.")], "response": "Page of TenantResponse", "response_fields": [("id", "long", "Unique tenant identifier."), ("name", "string", "Tenant name."), ("status", "string", "Tenant status (ACTIVE, INACTIVE, SUSPENDED)."), ("createdAt", "datetime", "Creation timestamp.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Tenant Management", "method": "GET", "path": "/api/management/tenants/{id}", "summary": "Retrieve a tenant by ID", "auth": "Authenticated - requires TENANT_READ", "description": "Returns the full details of a specific tenant. Tenant access is checked via AuthorizationService.", "request_body": [], "path_params": [("id", "long", "Required. The tenant ID.")], "response": "TenantResponse", "response_fields": [("id", "long", "Unique tenant identifier."), ("name", "string", "Tenant name."), ("status", "string", "Tenant status."), ("createdAt", "datetime", "Creation timestamp.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "Tenant Management", "method": "POST", "path": "/api/management/tenants", "summary": "Create a new tenant", "auth": "Authenticated - requires TENANT_CREATE", "description": "Creates a new tenant with the specified name and status. Duplicate tenant names are rejected.", "request_body": [("name", "string", "Required. Unique tenant name.", "NotBlank, Size(max=100)"), ("status", "string", "Required. Initial status: ACTIVE, INACTIVE, or SUSPENDED.", "NotNull")], "response": "TenantResponse", "response_fields": [("id", "long", "Unique tenant identifier."), ("name", "string", "Created tenant name."), ("status", "string", "Initial status."), ("createdAt", "datetime", "Creation timestamp.")], "status_codes": ["201 Created", "400 Bad Request", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Tenant Management", "method": "PUT", "path": "/api/management/tenants/{id}", "summary": "Update a tenant", "auth": "Authenticated - requires TENANT_UPDATE", "description": "Updates the name or status of a tenant. Tenant management access is enforced.", "request_body": [("name", "string", "Required. Updated tenant name.", "NotBlank, Size(max=100)"), ("status", "string", "Required. Updated status.", "NotNull")], "path_params": [("id", "long", "Required. The tenant ID.")], "response": "TenantResponse", "response_fields": [("id", "long", "Unique tenant identifier."), ("name", "string", "Updated tenant name."), ("status", "string", "Updated status.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Tenant Management", "method": "DELETE", "path": "/api/management/tenants/{id}", "summary": "Delete a tenant", "auth": "Authenticated - requires TENANT_DELETE", "description": "Permanently removes a tenant. Tenant management access is enforced.", "request_body": [], "path_params": [("id", "long", "Required. The tenant ID.")], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "Department Management", "method": "GET", "path": "/api/management/departments", "summary": "List departments with pagination", "auth": "Authenticated - requires DEPARTMENT_READ", "description": "Returns a paginated list of departments. Super admin sees all; tenant admin sees only their tenant departments.", "request_body": [], "query_params": [("page", "int", "Optional. Page number (0-based). Default: 0."), ("size", "int", "Optional. Page size. Default: 20."), ("sort", "string", "Optional. Sort field and direction.")], "response": "Page of DepartmentResponse", "response_fields": [("id", "long", "Unique department identifier."), ("name", "string", "Department name."), ("tenantId", "long", "Parent tenant ID."), ("tenantName", "string", "Parent tenant name."), ("managerIds", "long[]", "List of manager user IDs."), ("managerUsernames", "string[]", "List of manager usernames.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Department Management", "method": "GET", "path": "/api/management/departments/{id}", "summary": "Retrieve a department by ID", "auth": "Authenticated - requires DEPARTMENT_READ", "description": "Returns the full details of a specific department including manager information. Tenant-scoped access is enforced.", "request_body": [], "path_params": [("id", "long", "Required. The department ID.")], "response": "DepartmentResponse", "response_fields": [("id", "long", "Unique department identifier."), ("name", "string", "Department name."), ("tenantId", "long", "Parent tenant ID."), ("tenantName", "string", "Parent tenant name."), ("managerIds", "long[]", "Manager user IDs."), ("managerUsernames", "string[]", "Manager usernames.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "Department Management", "method": "POST", "path": "/api/management/departments", "summary": "Create a new department", "auth": "Authenticated - requires DEPARTMENT_CREATE", "description": "Creates a new department within the specified tenant. Duplicate department names within the same tenant are rejected.", "request_body": [("name", "string", "Required. Department name.", "NotBlank, Size(max=100)"), ("tenantId", "long", "Required. Parent tenant ID.", "NotNull"), ("managerIds", "long[]", "Optional. List of manager user IDs.", "None")], "response": "DepartmentResponse", "response_fields": [("id", "long", "Unique department identifier."), ("name", "string", "Created department name."), ("tenantName", "string", "Parent tenant name."), ("managerUsernames", "string[]", "Manager usernames.")], "status_codes": ["201 Created", "400 Bad Request", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Department Management", "method": "PUT", "path": "/api/management/departments/{id}", "summary": "Update a department", "auth": "Authenticated - requires DEPARTMENT_UPDATE", "description": "Updates the name or manager list of a department. Department management access is enforced.", "request_body": [("name", "string", "Required. Updated department name.", "NotBlank, Size(max=100)"), ("managerIds", "long[]", "Optional. Updated manager user IDs.", "None")], "path_params": [("id", "long", "Required. The department ID.")], "response": "DepartmentResponse", "response_fields": [("id", "long", "Unique department identifier."), ("name", "string", "Updated department name."), ("managerUsernames", "string[]", "Updated manager usernames.")], "status_codes": ["200 OK", "400 Bad Request", "401 Unauthorized", "403 Forbidden", "409 Conflict"]},
    {"group": "Department Management", "method": "DELETE", "path": "/api/management/departments/{id}", "summary": "Delete a department", "auth": "Authenticated - requires DEPARTMENT_DELETE", "description": "Permanently removes a department. Department management access is enforced.", "request_body": [], "path_params": [("id", "long", "Required. The department ID.")], "response": "Void", "response_fields": [], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "Audit Log", "method": "GET", "path": "/api/management/audit", "summary": "List audit log entries", "auth": "Authenticated - requires AUDIT_LOG_READ", "description": "Returns a paginated list of audit log entries. Super admin sees all entries; tenant admin sees only their tenant entries.", "request_body": [], "query_params": [("page", "int", "Optional. Page number (0-based). Default: 0."), ("size", "int", "Optional. Page size. Default: 20."), ("sort", "string", "Optional. Sort field and direction.")], "response": "Page of AuditLogResponse", "response_fields": [("id", "long", "Unique log entry identifier."), ("actorId", "long", "ID of the user who performed the action."), ("actorUsername", "string", "Username of the actor."), ("tenantId", "long", "Tenant where the action occurred."), ("action", "string", "Action performed (e.g., USER_CREATED, EXPENSE_APPROVED)."), ("resourceType", "string", "Type of resource affected (USER, EXPENSE, TENANT)."), ("resourceId", "string", "ID of the affected resource."), ("details", "string", "Additional details about the action."), ("timestamp", "datetime", "When the action occurred.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden"]},
    {"group": "Audit Log", "method": "GET", "path": "/api/management/audit/{id}", "summary": "Retrieve a single audit log entry", "auth": "Authenticated - requires AUDIT_LOG_READ", "description": "Returns the full details of a specific audit log entry. Tenant-scoped access is enforced.", "request_body": [], "path_params": [("id", "long", "Required. The audit log entry ID.")], "response": "AuditLogResponse", "response_fields": [("id", "long", "Unique log entry identifier."), ("actorId", "long", "ID of the actor."), ("actorUsername", "string", "Username of the actor."), ("tenantId", "long", "Tenant ID."), ("action", "string", "Action performed."), ("resourceType", "string", "Resource type."), ("resourceId", "string", "Resource ID."), ("details", "string", "Action details."), ("timestamp", "datetime", "Action timestamp.")], "status_codes": ["200 OK", "401 Unauthorized", "403 Forbidden", "404 Not Found"]},
    {"group": "System / Actuator", "method": "GET", "path": "/actuator/health", "summary": "Application health check", "auth": "None (Public)", "description": "Returns the health status of the application and its dependencies. Basic status is always available; detailed information requires authentication.", "request_body": [], "response": "HealthIndicator", "response_fields": [("status", "string", "UP, DOWN, or OUT_OF_SERVICE."), ("components", "object", "Detailed health of each dependency (when authorized).")], "status_codes": ["200 OK"]},
    {"group": "System / Actuator", "method": "GET", "path": "/actuator/info", "summary": "Application information", "auth": "Authenticated", "description": "Returns general application information and metadata.", "request_body": [], "response": "Info", "response_fields": [("build", "object", "Build information (version, artifact)."), ("git", "object", "Git commit information.")], "status_codes": ["200 OK", "401 Unauthorized"]},
    {"group": "System / Actuator", "method": "GET", "path": "/actuator/metrics", "summary": "Application metrics", "auth": "Authenticated", "description": "Returns a list of all available metric names for monitoring.", "request_body": [], "response": "Metrics", "response_fields": [("names", "string[]", "Available metric names.")], "status_codes": ["200 OK", "401 Unauthorized"]},
    {"group": "System / Actuator", "method": "GET", "path": "/actuator/prometheus", "summary": "Prometheus metrics endpoint", "auth": "Authenticated", "description": "Returns application metrics in Prometheus exposition format for scraping by a Prometheus server.", "request_body": [], "response": "text/plain (Prometheus format)", "response_fields": [], "status_codes": ["200 OK", "401 Unauthorized"]},
]

PERMISSIONS = [
    ("TENANT_READ", "Read tenant information"),
    ("TENANT_CREATE", "Create new tenants"),
    ("TENANT_UPDATE", "Update tenant details"),
    ("TENANT_DELETE", "Remove tenants"),
    ("USER_READ", "View user accounts"),
    ("USER_WRITE", "Modify user profiles and MFA settings"),
    ("USER_CREATE", "Create new user accounts"),
    ("USER_UPDATE", "Update user information"),
    ("USER_DELETE", "Remove user accounts"),
    ("USER_ENABLE", "Enable or disable user accounts"),
    ("USER_ASSIGN_ROLE", "Assign or remove roles from users"),
    ("ROLE_READ", "View role definitions"),
    ("ROLE_WRITE", "Modify role details"),
    ("ROLE_DELETE", "Remove custom roles"),
    ("ROLE_ASSIGN_PERMISSION", "Add or remove permissions from roles"),
    ("DEPARTMENT_READ", "View department information"),
    ("DEPARTMENT_CREATE", "Create new departments"),
    ("DEPARTMENT_UPDATE", "Modify department details"),
    ("DEPARTMENT_DELETE", "Remove departments"),
    ("EXPENSE_READ", "View expenses"),
    ("EXPENSE_READ_ALL", "View all expenses across tenants"),
    ("EXPENSE_CREATE", "Submit new expenses"),
    ("EXPENSE_UPDATE", "Modify or cancel pending expenses"),
    ("EXPENSE_DELETE", "Delete expenses"),
    ("EXPENSE_APPROVE", "Approve pending expenses"),
    ("EXPENSE_REJECT", "Reject pending expenses"),
    ("EXPENSE_PROCESS", "Process approved expenses"),
    ("MFA_MANAGE", "Manage multi-factor authentication"),
    ("REPORT_READ", "View reports"),
    ("AUDIT_LOG_READ", "View audit log entries"),
]

ROLES = [
    ("PLATFORM_ADMIN", "Full system access, bypasses tenant isolation"),
    ("TENANT_ADMIN", "Full access within assigned tenant"),
    ("USER_MANAGER", "Manage users within tenant"),
    ("DEPARTMENT_MANAGER", "Manage assigned departments"),
    ("EMPLOYEE", "Basic access - submit and view own expenses"),
    ("AUDITOR", "Read-only access to audit logs and reports"),
    ("FINANCE", "Process approved expenses"),
]

AUDIT_ACTIONS = [
    ("USER_CREATED", "A new user account was created"),
    ("USER_UPDATED", "A user profile was modified"),
    ("USER_DELETED", "A user account was removed"),
    ("USER_ENABLED", "A user account was enabled"),
    ("USER_DISABLED", "A user account was disabled"),
    ("USER_ROLE_ASSIGNED", "A role was assigned to a user"),
    ("USER_ROLE_REMOVED", "A role was removed from a user"),
    ("USER_MFA_ENABLED", "MFA was enabled for a user"),
    ("USER_MFA_DISABLED", "MFA was disabled for a user"),
    ("USER_MFA_RESET", "MFA was reset for a user"),
    ("EXPENSE_CREATED", "A new expense was submitted"),
    ("EXPENSE_UPDATED", "An expense was modified"),
    ("EXPENSE_CANCELLED", "An expense was cancelled"),
    ("EXPENSE_APPROVED", "An expense was approved"),
    ("EXPENSE_REJECTED", "An expense was rejected"),
    ("EXPENSE_PROCESSED", "An expense was processed by finance"),
    ("TENANT_CREATED", "A new tenant was created"),
    ("TENANT_UPDATED", "A tenant was modified"),
    ("TENANT_DELETED", "A tenant was removed"),
]

ERROR_CODES = [
    ("400 Bad Request", "Validation failed, duplicate constraint, or invalid input", "MethodArgumentNotValidException, DataIntegrityViolationException, IllegalArgumentException"),
    ("401 Unauthorized", "Invalid or missing credentials", "BadCredentialsException, UsernameNotFoundException"),
    ("403 Forbidden", "Insufficient permissions or tenant boundary violation", "AccessDeniedException"),
    ("409 Conflict", "Invalid state transition (e.g., approving already-rejected expense)", "IllegalStateException"),
    ("429 Too Many Requests", "Account locked due to too many failed login attempts", "LockedException"),
    ("500 Internal Server Error", "Unexpected server error", "Exception (catch-all)"),
]


def build_styles():
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name="CoverTitle", fontName="Helvetica-Bold", fontSize=28, leading=36.4, textColor=PRIMARY_ACCENT, alignment=TA_CENTER, spaceAfter=12))
    styles.add(ParagraphStyle(name="CoverSubtitle", fontName="Helvetica", fontSize=14, leading=18.2, textColor=NEUTRAL_DARK, alignment=TA_CENTER, spaceAfter=6))
    styles.add(ParagraphStyle(name="CoverMeta", fontName="Helvetica", fontSize=10, leading=13, textColor=NEUTRAL_DARK, alignment=TA_CENTER, spaceAfter=4))
    styles.add(ParagraphStyle(name="H1", fontName="Helvetica-Bold", fontSize=20, leading=26, textColor=PRIMARY_ACCENT, spaceBefore=18, spaceAfter=10, keepWithNext=True))
    styles.add(ParagraphStyle(name="H2", fontName="Helvetica-Bold", fontSize=15, leading=19.5, textColor=SECONDARY_ACCENT, spaceBefore=14, spaceAfter=8, keepWithNext=True))
    styles.add(ParagraphStyle(name="H3", fontName="Helvetica-Bold", fontSize=12, leading=15.6, textColor=PRIMARY_ACCENT, spaceBefore=10, spaceAfter=6, keepWithNext=True))
    styles.add(ParagraphStyle(name="Body", fontName="Helvetica", fontSize=10, leading=13, textColor=NEUTRAL_DARK, alignment=TA_JUSTIFY, spaceAfter=6))
    styles.add(ParagraphStyle(name="EndpointTitle", fontName="Helvetica-Bold", fontSize=11, leading=14.3, textColor=PRIMARY_ACCENT, spaceBefore=8, spaceAfter=4, keepWithNext=True))
    styles.add(ParagraphStyle(name="CellText", fontName="Helvetica", fontSize=8.5, leading=11.05, textColor=NEUTRAL_DARK))
    styles.add(ParagraphStyle(name="CellBold", fontName="Helvetica-Bold", fontSize=8.5, leading=11.05, textColor=NEUTRAL_DARK))
    styles.add(ParagraphStyle(name="CellHeader", fontName="Helvetica-Bold", fontSize=8.5, leading=11.05, textColor=WHITE))
    styles.add(ParagraphStyle(name="TOCEntry", fontName="Helvetica", fontSize=11, leading=16, textColor=NEUTRAL_DARK, leftIndent=12, spaceAfter=4))
    return styles


def method_color_hex(method):
    return {"GET": "#059669", "POST": "#2563EB", "PUT": "#D97706", "PATCH": "#7C3AED", "DELETE": "#DC2626"}.get(method, "#374151")


def cell(text, styles, bold=False):
    style = "CellBold" if bold else "CellText"
    return Paragraph(str(text), styles[style])


def make_table(headers, rows, col_widths, styles):
    data = [[Paragraph(h, styles["CellHeader"]) for h in headers]]
    for row in rows:
        data.append([cell(c, styles) if not isinstance(c, Paragraph) else c for c in row])
    t = Table(data, colWidths=col_widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), PRIMARY_ACCENT),
        ("TEXTCOLOR", (0, 0), (-1, 0), WHITE),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, 0), 8.5),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 6),
        ("TOPPADDING", (0, 0), (-1, 0), 6),
        ("BACKGROUND", (0, 1), (-1, -1), WHITE),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [WHITE, NEUTRAL_LIGHT]),
        ("GRID", (0, 0), (-1, -1), 0.5, LIGHT_BORDER),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 1), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 5),
    ]))
    return t


def build_endpoint_section(ep, styles):
    elements = []
    method = ep["method"]
    color = method_color_hex(method)
    title = f'<font color="{color}">{method}</font>  {ep["path"]}'
    elements.append(Paragraph(title, styles["EndpointTitle"]))
    elements.append(Paragraph(f'<b>Summary:</b> {ep["summary"]}', styles["Body"]))
    elements.append(Paragraph(f'<b>Authorization:</b> {ep["auth"]}', styles["Body"]))
    elements.append(Paragraph(ep["description"], styles["Body"]))
    if ep.get("path_params"):
        elements.append(Paragraph("<b>Path Parameters</b>", styles["H3"]))
        rows = [(p[0], p[1], p[2]) for p in ep["path_params"]]
        elements.append(make_table(["Parameter", "Type", "Description"], rows, [100, 70, CONTENT_WIDTH - 170], styles))
    if ep.get("query_params"):
        elements.append(Paragraph("<b>Query Parameters</b>", styles["H3"]))
        rows = [(p[0], p[1], p[2]) for p in ep["query_params"]]
        elements.append(make_table(["Parameter", "Type", "Description"], rows, [100, 70, CONTENT_WIDTH - 170], styles))
    if ep.get("request_body"):
        elements.append(Paragraph("<b>Request Body</b>", styles["H3"]))
        rows = [(r[0], r[1], r[2], r[3]) for r in ep["request_body"]]
        elements.append(make_table(["Field", "Type", "Description", "Validation"], rows, [110, 60, CONTENT_WIDTH - 280, 110], styles))
    elements.append(Paragraph(f'<b>Response:</b> {ep["response"]}', styles["Body"]))
    if ep.get("response_fields"):
        elements.append(Paragraph("<b>Response Fields</b>", styles["H3"]))
        rows = [(f[0], f[1], f[2]) for f in ep["response_fields"]]
        elements.append(make_table(["Field", "Type", "Description"], rows, [120, 70, CONTENT_WIDTH - 190], styles))
    if ep.get("status_codes"):
        elements.append(Paragraph("<b>Status Codes</b>", styles["H3"]))
        rows = [(sc,) for sc in ep["status_codes"]]
        elements.append(make_table(["Status Code"], rows, [CONTENT_WIDTH], styles))
    elements.append(Spacer(1, 10))
    elements.append(HRFlowable(width="100%", thickness=0.5, color=LIGHT_BORDER, spaceAfter=6))
    return elements


def build_pdf(filename="opencode_project_api_spec.pdf"):
    styles = build_styles()
    story = []

    # Cover Page
    story.append(Spacer(1, 120))
    story.append(Paragraph("API Specification", styles["CoverTitle"]))
    story.append(Spacer(1, 12))
    story.append(HRFlowable(width="60%", thickness=2, color=SECONDARY_ACCENT, spaceAfter=16))
    story.append(Paragraph("Spring Boot Boilerplate", styles["CoverSubtitle"]))
    story.append(Paragraph("Enterprise Multi-Tenant Expense Management Platform", styles["CoverSubtitle"]))
    story.append(Spacer(1, 40))
    story.append(Paragraph("Version 1.0", styles["CoverMeta"]))
    story.append(Paragraph("Generated: August 2026", styles["CoverMeta"]))
    story.append(Paragraph("Base URL: http://localhost:8080", styles["CoverMeta"]))
    story.append(Paragraph("Authentication: JWT Bearer Token (stateless)", styles["CoverMeta"]))
    story.append(PageBreak())

    # Table of Contents
    story.append(Paragraph("Table of Contents", styles["H1"]))
    story.append(HRFlowable(width="100%", thickness=0.5, color=LIGHT_BORDER, spaceAfter=6))
    story.append(Spacer(1, 8))
    toc_entries = [
        ("1. Overview", "System architecture and general information"),
        ("2. Authentication", "Login, MFA, token refresh, password management"),
        ("3. Expenses", "Create, update, approve, reject, and process expenses"),
        ("4. User Management", "CRUD operations, role assignment, MFA management"),
        ("5. Role Management", "Create, update, and manage roles and permissions"),
        ("6. Tenant Management", "Multi-tenant administration"),
        ("7. Department Management", "Organizational structure management"),
        ("8. Audit Log", "System audit trail and compliance logging"),
        ("9. System / Actuator", "Health checks, metrics, and monitoring"),
        ("10. Security Model", "Permissions, roles, and authorization framework"),
        ("11. Error Handling", "Standard error responses and codes"),
    ]
    for title, desc in toc_entries:
        story.append(Paragraph(f'<b>{title}</b>  -  {desc}', styles["TOCEntry"]))
    story.append(PageBreak())

    # Overview
    story.append(Paragraph("1. Overview", styles["H1"]))
    story.append(HRFlowable(width="100%", thickness=0.5, color=LIGHT_BORDER, spaceAfter=6))
    story.append(Paragraph("This document provides a comprehensive specification of all available API endpoints for the Spring Boot Boilerplate platform. The platform is a multi-tenant enterprise expense management system built with Java 21, Spring Boot 3.2.5, and secured with stateless JWT authentication.", styles["Body"]))
    story.append(Spacer(1, 8))
    story.append(Paragraph("<b>Technology Stack</b>", styles["H3"]))
    overview_data = [
        ("Runtime", "Java 21, Spring Boot 3.2.5, Maven"),
        ("Database", "H2 (dev/test), PostgreSQL (production), Flyway migrations"),
        ("Security", "Spring Security, JWT (stateless), Method-level authorization"),
        ("Caching", "Redis"),
        ("Monitoring", "Spring Actuator, Prometheus metrics"),
        ("Testing", "JUnit 5, Mockito, AssertJ"),
    ]
    story.append(make_table(["Component", "Details"], overview_data, [120, CONTENT_WIDTH - 120], styles))
    story.append(Spacer(1, 10))
    story.append(Paragraph("<b>Base URL and Response Format</b>", styles["H3"]))
    story.append(Paragraph("All API endpoints are served from the root path (no context path prefix). All responses are wrapped in a standard envelope:", styles["Body"]))
    story.append(Paragraph('<font face="Courier" size="8.5">{"success": boolean, "message": string, "data": T, "timestamp": "ISO-8601"}</font>', styles["Body"]))
    story.append(Spacer(1, 6))
    story.append(Paragraph("<b>Endpoint Summary</b>", styles["H3"]))
    summary_data = [
        ("Total Endpoints", "42 custom + 4 actuator"),
        ("Public Endpoints", "5 (login, refresh, forgot-password, reset-password, mfa/verify)"),
        ("Authenticated-only", "3 (logout, /me, change-password)"),
        ("Permission-gated", "34 (via @PreAuthorize on service methods)"),
    ]
    story.append(make_table(["Metric", "Count / Detail"], summary_data, [140, CONTENT_WIDTH - 140], styles))
    story.append(PageBreak())

    # Endpoint Groups
    section_num = 2
    for group in ENDPOINT_GROUPS:
        group_endpoints = [ep for ep in ENDPOINTS if ep["group"] == group]
        story.append(Paragraph(f"{section_num}. {group}", styles["H1"]))
        story.append(HRFlowable(width="100%", thickness=0.5, color=LIGHT_BORDER, spaceAfter=6))
        story.append(Paragraph(f"This section documents all {group.lower()} endpoints. There are {len(group_endpoints)} endpoint(s) in this group.", styles["Body"]))
        story.append(Spacer(1, 8))
        for ep in group_endpoints:
            for elem in build_endpoint_section(ep, styles):
                story.append(elem)
        story.append(PageBreak())
        section_num += 1

    # Security Model
    story.append(Paragraph(f"{section_num}. Security Model", styles["H1"]))
    story.append(HRFlowable(width="100%", thickness=0.5, color=LIGHT_BORDER, spaceAfter=6))
    story.append(Paragraph("The platform implements a comprehensive role-based access control (RBAC) system with multi-tenancy. Authorization is enforced at the service layer using Spring Security method-level annotations.", styles["Body"]))
    story.append(Spacer(1, 8))
    story.append(Paragraph("<b>Built-in Roles</b>", styles["H3"]))
    roles_data = [(r[0], r[1]) for r in ROLES]
    story.append(make_table(["Role Name", "Description"], roles_data, [130, CONTENT_WIDTH - 130], styles))
    story.append(Spacer(1, 10))
    story.append(Paragraph("<b>Available Permissions</b>", styles["H3"]))
    perm_data = [(p[0], p[1]) for p in PERMISSIONS]
    story.append(make_table(["Permission", "Description"], perm_data, [160, CONTENT_WIDTH - 160], styles))
    story.append(Spacer(1, 10))
    story.append(Paragraph("<b>Audit Actions</b>", styles["H3"]))
    story.append(Paragraph("The following actions are recorded in the audit log for compliance and traceability:", styles["Body"]))
    audit_data = [(a[0], a[1]) for a in AUDIT_ACTIONS]
    story.append(make_table(["Action", "Description"], audit_data, [160, CONTENT_WIDTH - 160], styles))
    story.append(PageBreak())
    section_num += 1

    # Error Handling
    story.append(Paragraph(f"{section_num}. Error Handling", styles["H1"]))
    story.append(HRFlowable(width="100%", thickness=0.5, color=LIGHT_BORDER, spaceAfter=6))
    story.append(Paragraph("All error responses follow the standard API envelope with success=false. The GlobalExceptionHandler catches standard Java exceptions and maps them to appropriate HTTP status codes. No custom exception classes are used.", styles["Body"]))
    story.append(Spacer(1, 8))
    story.append(Paragraph("<b>Standard Error Codes</b>", styles["H3"]))
    err_data = [(e[0], e[1], e[2]) for e in ERROR_CODES]
    story.append(make_table(["HTTP Status", "Cause", "Exception Type"], err_data, [120, 200, CONTENT_WIDTH - 320], styles))
    story.append(Spacer(1, 10))
    story.append(Paragraph("<b>Password Policy</b>", styles["H3"]))
    story.append(Paragraph("All passwords must meet the following requirements enforced by the custom @Password validator:", styles["Body"]))
    pwd_data = [
        ("Minimum Length", "8 characters"),
        ("Uppercase Letters", "At least one (A-Z)"),
        ("Lowercase Letters", "At least one (a-z)"),
        ("Digits", "At least one (0-9)"),
        ("Special Characters", "At least one from: !@#$%^&*()-_+=[]{};':\"|,.<>/?~"),
    ]
    story.append(make_table(["Requirement", "Rule"], pwd_data, [140, CONTENT_WIDTH - 140], styles))

    # Build
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=MARGIN,
        rightMargin=MARGIN,
        topMargin=MARGIN,
        bottomMargin=MARGIN,
        title="API Specification - Spring Boot Boilerplate",
        author="API Integration Manager",
    )
    doc.build(story)
    print(f"PDF generated: {filename}")


if __name__ == "__main__":
    build_pdf()
