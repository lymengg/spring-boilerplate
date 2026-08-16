# Functional Requirements

## 1. Introduction

### Purpose
This document describes the functional behavior and business rules of the multi-tenant expense management system based on the current implementation.

### Scope
This requirements document covers:
- Authentication and session management
- Multi-factor authentication
- User, tenant, department, and role management
- Expense lifecycle management (submission, approval, processing)
- Audit logging
- Password management
- Security controls (rate limiting, account lockout)

### Exclusions
- Frontend user interface (API-only backend)
- Payment gateway integration
- File/document attachment storage
- Reporting and analytics dashboards
- External identity provider (SSO) integration
- Integration with external accounting or ERP systems

---

## 2. Actors

| Actor | Description | Capabilities |
|-------|-------------|-------------|
| **Unauthenticated User** | A person who has not logged in | Login, request password reset, reset password with token |
| **Super Admin** | System administrator with ADMIN role and no tenant assignment | Full access to all tenants, users, roles, departments, expenses, and audit logs |
| **Tenant Administrator** | User with ADMIN role assigned to a specific tenant | Manage users, roles, departments within their tenant; view tenant expenses and audit logs |
| **User Manager** | User with USER_MANAGER role within a tenant | Create and manage users within their tenant; assign roles (except ADMIN and USER_MANAGER) |
| **Manager** | User with MANAGER role in a department | View department expenses; approve or reject pending expenses |
| **Employee** | User with EMPLOYEE role | Create, view, edit, and cancel own expenses |
| **Finance Officer** | User with FINANCE role | View approved expenses; process expenses for payment |
| **Auditor** | User with AUDITOR role | Read-only access to all tenant data including expenses, users, departments, and audit logs |
| **System (Automated)** | Internal system processes | Scheduled cleanup of expired password reset tokens; audit log creation |

---

## 3. Authentication Requirements

### FR-AUTH-001: User Login
- **Actor:** Unauthenticated User
- **Preconditions:** User account exists and is enabled
- **Expected Behavior:**
  - User provides username (or email) and password
  - System validates credentials against stored BCrypt hash
  - If valid and MFA is not enabled: issue access token (15 min) and refresh token (7 days)
  - If valid and MFA is enabled: return MFA session token (5 min expiry) instead of access tokens
  - If invalid: return "Invalid credentials" (generic message, no user enumeration)
  - If account is locked: return lockout message with remaining duration
- **Error Conditions:**
  - Invalid credentials → 401 Unauthorized
  - Account locked → 429 Too Many Requests
  - Account disabled → 401 Unauthorized

### FR-AUTH-002: Token Refresh
- **Actor:** Authenticated User (with valid refresh token)
- **Preconditions:** User has a valid, non-revoked refresh token
- **Expected Behavior:**
  - User provides refresh token
  - System validates token hash against Redis store
  - System revokes the old refresh token
  - System issues new access token and refresh token pair
  - Old refresh token is marked as replaced
- **Error Conditions:**
  - Invalid/expired/revoked refresh token → 401 Unauthorized

### FR-AUTH-003: User Logout
- **Actor:** Authenticated User
- **Preconditions:** User is authenticated
- **Expected Behavior:**
  - System revokes all refresh tokens for the current user
  - Client discards access token
  - Audit log entry created
- **Result:** User session is terminated

### FR-AUTH-004: Get Current User Profile
- **Actor:** Authenticated User
- **Preconditions:** User is authenticated
- **Expected Behavior:**
  - System returns current user's profile including username, email, roles, tenant, and department
- **Result:** Profile data returned

---

## 4. Authorization Requirements

### Role/Functionality Matrix

| Functionality | ADMIN | USER_MANAGER | MANAGER | EMPLOYEE | AUDITOR | FINANCE |
|--------------|-------|-------------|---------|----------|---------|---------|
| **Tenant Management** |
| List tenants | Yes | No | No | No | No | No |
| Create tenant | Yes | No | No | No | No | No |
| Update tenant | Yes | No | No | No | No | No |
| Delete tenant | Yes | No | No | No | No | No |
| **User Management** |
| List users | Yes (all) | Yes (tenant) | Yes (read only) | No | Yes (read only) | No |
| Create user | Yes | Yes (tenant) | No | No | No | No |
| Update user | Yes | Yes (tenant) | No | No | No | No |
| Delete user | Yes | Yes (tenant) | No | No | No | No |
| Enable/disable user | Yes | Yes (tenant) | No | No | No | No |
| Assign role | Yes | Yes (limited) | No | No | No | No |
| **Department Management** |
| List departments | Yes | Yes | Yes | No | Yes | No |
| Create department | Yes | Yes | No | No | No | No |
| Update department | Yes | Yes | No | No | No | No |
| Delete department | Yes | Yes | No | No | No | No |
| **Role Management** |
| List roles | Yes | Yes (read) | Yes (read) | No | No | No |
| Create role | Yes | No | No | No | No | No |
| Update role | Yes | No | No | No | No | No |
| Delete role | Yes | No | No | No | No | No |
| Assign permission | Yes | No | No | No | No | No |
| **Expense Management** |
| List expenses | Yes (all) | Yes (tenant) | Yes (dept) | Yes (own) | Yes (all tenant) | Yes (approved) |
| Create expense | Yes | Yes | Yes | Yes | No | No |
| Update expense | Yes | Yes | Yes (dept) | Yes (own, PENDING) | No | No |
| Cancel expense | Yes | Yes | Yes (dept) | Yes (own, PENDING) | No | No |
| Approve expense | Yes | No | Yes (dept) | No | No | No |
| Reject expense | Yes | No | Yes (dept) | No | No | No |
| Process expense | Yes | No | No | No | No | Yes |
| **Audit Log** |
| View audit logs | Yes (all) | No | No | No | Yes (tenant) | No |

### FR-AUTHZ-001: Tenant Isolation
- **Actor:** All authenticated users (except super admin)
- **Preconditions:** User belongs to a tenant
- **Expected Behavior:**
  - All data queries are automatically scoped to the user's tenant
  - Users cannot access data from other tenants
  - Super admins (ADMIN with no tenant) bypass tenant isolation

### FR-AUTHZ-002: Privilege Hierarchy
- **Actor:** All users performing management operations
- **Preconditions:** User is attempting to modify another user
- **Expected Behavior:**
  - Users cannot update, delete, or modify roles of users with equal or higher permissions
  - The system compares the total permission count of the acting user and target user

### FR-AUTHZ-003: Last Admin Protection
- **Actor:** All users performing user management operations
- **Preconditions:** Target user is the last user with ADMIN role in their tenant
- **Expected Behavior:**
  - System prevents deletion or disabling of the last admin in a tenant
  - System prevents removal of the last ADMIN role assignment

---

## 5. Functional Modules

### 5.1 Authentication Module

**Purpose:** Secure user login, token management, and session lifecycle.

**Preconditions:** System is running; Redis is available for token storage.

**Main Flow:**
1. User submits credentials via POST /api/auth/login
2. System validates username/email exists
3. System verifies password against BCrypt hash
4. System checks account lockout status
5. If MFA is enabled, return MFA session token
6. If MFA is not enabled, issue access and refresh tokens
7. Record audit log entry

**Alternative Flows:**
- If credentials are invalid: return generic error message
- If account is locked: return lockout duration
- If account is disabled: return authentication failure

**Validation:**
- Username/email must not be blank
- Password must not be blank

**Authorization:** Public endpoint (no authentication required)

**Error Conditions:**
- Invalid credentials → 401
- Account locked → 429
- Rate limit exceeded → 429

**Result:** JWT tokens returned or MFA challenge initiated

---

### 5.2 Multi-Factor Authentication Module

**Purpose:** Provide additional security via TOTP or email OTP verification.

**Preconditions:** User is authenticated; email service is configured for EMAIL method.

**Main Flow:**
1. User initiates MFA setup via POST /api/mfa/enable
2. System generates secret (TOTP) or sends OTP (EMAIL)
3. User verifies code via POST /api/mfa/verify-setup
4. MFA is activated for the user

**Alternative Flows:**
- If MFA is already enabled: return current status
- If verification fails: return error, allow retry
- If code expires: user must request new code

**Validation:**
- MFA method must be TOTP or EMAIL
- Verification code must be 6 digits

**Authorization:** Authenticated user (own account only)

**Error Conditions:**
- Invalid verification code → 400
- Code expired → 400
- Rate limit exceeded → 429

**Result:** MFA enabled/disabled; MFA status returned

---

### 5.3 User Management Module

**Purpose:** Create, read, update, delete, and manage user accounts and role assignments.

**Preconditions:** Actor has appropriate user management permissions.

**Main Flow:**
1. Authorized user creates/updates/deletes user via API
2. System validates permissions and business rules
3. System persists changes
4. Audit log entry created

**Alternative Flows:**
- If target user has higher privileges: operation denied
- If attempting to delete last admin: operation denied
- If username/email already exists: validation error

**Validation:**
- Username: required, unique
- Email: required, valid format, unique
- Password: required on creation, must meet complexity rules
- Names: required, within length limits

**Authorization:** USER_CREATE, USER_READ, USER_WRITE, USER_DELETE, USER_ENABLE, USER_ASSIGN_ROLE

**Business Rules:**
- User manager can only manage users in their own tenant
- Only ADMIN can assign ADMIN or USER_MANAGER roles
- Users cannot grant permissions they do not possess

**Error Conditions:**
- Duplicate username/email → 400
- Insufficient permissions → 403
- Last admin protection → 400
- Privilege hierarchy violation → 403

**Result:** User created/updated/deleted; audit log recorded

---

### 5.4 Tenant Management Module

**Purpose:** Manage organizations (tenants) that use the platform.

**Preconditions:** Actor has TENANT_* permissions.

**Main Flow:**
1. Super admin creates/updates/deletes tenant via API
2. System validates uniqueness and status
3. System persists changes
4. Audit log entry created

**Validation:**
- Name: required, unique
- Status: must be ACTIVE, INACTIVE, or SUSPENDED

**Authorization:** TENANT_READ, TENANT_CREATE, TENANT_UPDATE, TENANT_DELETE (super admin only)

**Error Conditions:**
- Duplicate tenant name → 400
- Insufficient permissions → 403

**Result:** Tenant created/updated/deleted; audit log recorded

---

### 5.5 Department Management Module

**Purpose:** Manage organizational units within a tenant.

**Preconditions:** Actor has DEPARTMENT_* permissions; tenant is active.

**Main Flow:**
1. Authorized user creates/updates/department via API
2. System validates tenant scope and uniqueness
3. System persists changes
4. Audit log entry created

**Validation:**
- Name: required, unique within tenant
- Manager: must be a valid user in the same tenant

**Authorization:** DEPARTMENT_READ, DEPARTMENT_CREATE, DEPARTMENT_UPDATE, DEPARTMENT_DELETE

**Error Conditions:**
- Duplicate department name in tenant → 400
- Manager not in same tenant → 400

**Result:** Department created/updated/deleted; audit log recorded

---

### 5.6 Role Management Module

**Purpose:** Define and manage roles with associated permissions.

**Preconditions:** Actor has ROLE_* permissions.

**Main Flow:**
1. Admin creates/updates custom role via API
2. System validates against built-in role protection
3. System persists changes
4. Audit log entry created

**Validation:**
- Name: required
- Permissions: must be valid permission values
- Built-in roles cannot be modified or deleted

**Authorization:** ROLE_READ, ROLE_WRITE, ROLE_DELETE, ROLE_ASSIGN_PERMISSION

**Business Rules:**
- Built-in roles (ADMIN, USER, USER_MANAGER, MANAGER, EMPLOYEE, AUDITOR, FINANCE) are immutable
- Roles assigned to any user cannot be deleted
- Permission changes take effect on next token refresh

**Error Conditions:**
- Attempt to modify built-in role → 400
- Attempt to delete role assigned to users → 400
- Invalid permission value → 400

**Result:** Role created/updated/deleted; audit log recorded

---

### 5.7 Expense Management Module

**Purpose:** Manage the full expense lifecycle from submission through payment processing.

**Preconditions:** Actor has appropriate expense permissions; user belongs to a tenant.

**Main Flow:**
1. Employee creates expense via POST /api/expenses
2. System assigns PENDING status
3. Manager reviews and approves/rejects
4. Finance processes approved expenses

**Alternative Flows:**
- Employee can edit/cancel PENDING expenses
- Employee can cancel submitted expenses
- Rejected expenses remain in REJECTED status (no resubmit)

**Validation:**
- Title: required
- Amount: required, positive, max 4 decimal places
- Category: required
- Department: optional (defaults to user's department)

**Authorization:**
- EXPENSE_CREATE: create new expenses
- EXPENSE_READ: view expenses (scope varies by role)
- EXPENSE_UPDATE: edit/cancel expenses
- EXPENSE_APPROVE: approve pending expenses
- EXPENSE_REJECT: reject pending expenses
- EXPENSE_PROCESS: process approved expenses

**Business Rules:**
- Only PENDING expenses can be edited or cancelled
- Only PENDING expenses can be approved or rejected
- Only APPROVED expenses can be processed
- Approver must be a tenant manager or department manager
- Processor must have EXPENSE_PROCESS authority (FINANCE role)
- Owner or authorized manager can edit PENDING expenses

**Viewing Scope by Role:**
- Super admin: all expenses across all tenants
- AUDITOR: all expenses in their tenant
- MANAGER: expenses in their department
- FINANCE: approved expenses in their tenant
- EMPLOYEE: only their own expenses

**Error Conditions:**
- Invalid status transition → 409
- Insufficient permissions → 403
- Expense not found → 404
- User not in tenant → 403

**Result:** Expense status updated; audit log recorded with approver/processor identity

---

### 5.8 Audit Logging Module

**Purpose:** Record all significant system events for compliance and troubleshooting.

**Preconditions:** System is operational.

**Main Flow:**
1. System operation occurs (login, user CRUD, expense lifecycle event)
2. System creates audit log entry with actor, action, resource, tenant, and details
3. Entry is persisted to database

**Authorization:**
- AUDIT_LOG_READ: view audit logs (scope varies by role)
- Super admin sees all logs; others see only their tenant's logs

**Business Rules:**
- Audit logs are append-only (no update or delete operations exposed)
- Each entry includes: actor ID, actor username, tenant ID, action, resource type, resource ID, details, timestamp

**Error Conditions:**
- Audit log creation failure does not block the originating operation

**Result:** Audit log entry persisted

---

### 5.9 Password Management Module

**Purpose:** Secure password change, reset, and policy enforcement.

**Preconditions:** Email service configured for reset flow.

**Main Flow:**
1. User requests password reset via POST /api/auth/forgot-password
2. System generates single-use token (15-min expiry)
3. System sends reset link via email
4. User clicks link and submits new password via POST /api/auth/reset-password
5. System validates token and password complexity
6. System updates password and revokes all refresh tokens

**Alternative Flows:**
- Authenticated user changes password via POST /api/auth/change-password
- Requires current password verification
- All refresh tokens revoked after change

**Validation:**
- New password: minimum 8 characters, uppercase, lowercase, digit, special character
- Current password: required for change; verified against BCrypt hash
- Reset token: must be valid, unused, and unexpired

**Authorization:**
- Forgot password: public (rate-limited per email)
- Reset password: public (rate-limited per user, requires valid token)
- Change password: authenticated (own account)

**Business Rules:**
- Reset tokens are single-use (marked with usedAt timestamp)
- Expired or used tokens are cleaned up by daily scheduled job
- All refresh tokens are revoked after password change or reset

**Error Conditions:**
- Invalid/expired token → 400
- Password complexity not met → 400
- Current password incorrect → 400
- Rate limit exceeded → 429

**Result:** Password updated; all sessions invalidated

---

### 5.10 Account Security Module

**Purpose:** Protect accounts from brute force and unauthorized access.

**Preconditions:** Redis is available for rate limiting.

**Main Flow:**
1. System tracks failed login attempts per user
2. After 5 consecutive failures, account is locked for 15 minutes
3. Rate limiting enforced on sensitive endpoints

**Rate Limits:**
- Forgot password: 10 requests per 60-second window per email
- Reset password: 10 requests per 60-second window per user
- MFA verify: 10 requests per 60-second window per user

**Business Rules:**
- Failed attempts reset on successful login
- Lockout auto-expires after 15 minutes (checked on next attempt)
- Rate limit counters stored in Redis with sliding window algorithm

**Error Conditions:**
- Account locked → 429 with lockout duration
- Rate limit exceeded → 429

**Result:** Account protected; audit log recorded on lock/unlock

---

## 6. User Management

### FR-UM-001: User Creation
- **Actor:** ADMIN or USER_MANAGER
- **Preconditions:** Actor has USER_CREATE permission; username and email are unique
- **Expected Behavior:**
  - System creates user with provided username, email, first name, last name
  - Password must meet complexity requirements
  - User is assigned to actor's tenant (user manager) or specified tenant (admin)
  - Default role is USER (user manager) or as specified (admin)
  - Account is enabled by default
- **Business Rules:**
  - User manager can only create users in their own tenant
  - Only ADMIN can assign ADMIN or USER_MANAGER roles
  - No self-registration exists (API endpoint returns 401)

### FR-UM-002: User Update
- **Actor:** ADMIN or USER_MANAGER
- **Preconditions:** Actor has USER_WRITE permission; target user exists; privilege hierarchy satisfied
- **Expected Behavior:**
  - System updates user's first name and last name
  - System validates privilege hierarchy
- **Business Rules:**
  - Cannot update users with higher privileges
  - User manager scoped to own tenant

### FR-UM-003: User Deletion
- **Actor:** ADMIN or USER_MANAGER
- **Preconditions:** Actor has USER_DELETE permission; target user exists; not last admin; not self
- **Expected Behavior:**
  - System deletes the user account
  - System prevents deletion of last admin
  - System prevents self-deletion
- **Business Rules:**
  - Privilege hierarchy enforced
  - User manager scoped to own tenant

### FR-UM-004: User Enable/Disable
- **Actor:** ADMIN or USER_MANAGER
- **Preconditions:** Actor has USER_ENABLE permission; target user exists; not last admin
- **Expected Behavior:**
  - System toggles user's enabled status
  - System prevents disabling last admin
- **Business Rules:**
  - Cannot change own enabled state
  - Privilege hierarchy enforced

### FR-UM-005: Role Assignment
- **Actor:** ADMIN or USER_MANAGER
- **Preconditions:** Actor has USER_ASSIGN_ROLE permission; role exists; privilege hierarchy satisfied
- **Expected Behavior:**
  - System assigns role to user
  - System validates that granter has sufficient permissions
- **Business Rules:**
  - Only ADMIN can assign ADMIN or USER_MANAGER roles
  - Users cannot grant permissions they do not possess
  - Cannot remove last ADMIN role from any user

### FR-UM-006: User Retrieval
- **Actor:** ADMIN, USER_MANAGER, MANAGER, AUDITOR
- **Preconditions:** Actor has USER_READ permission
- **Expected Behavior:**
  - System returns paginated list of users
  - Scope: super admin sees all; tenant users see only their tenant
  - Supports pagination parameters (page, size)

---

## 7. Business Processes

### 7.1 Expense Submission Process
1. Employee authenticates and obtains access token
2. Employee creates expense via POST /api/expenses with title, description, amount, category
3. System validates input fields
4. System assigns tenant from user's tenant assignment
5. System assigns department from user's department (or specified department)
6. System sets status to PENDING
7. System records submission date
8. Audit log entry created with EXPENSE_CREATED action
9. Expense is visible to managers in the department

### 7.2 Expense Approval Process
1. Manager authenticates and obtains access token
2. Manager views pending expenses via GET /api/expenses (filtered by department)
3. Manager selects expense and approves via POST /api/expenses/{id}/approve
4. System validates EXPENSE_APPROVE authority
5. System validates manager is tenant manager or department manager
6. System updates status to APPROVED
7. System records approver identity and decision date
8. Audit log entry created with EXPENSE_APPROVED action
9. Expense becomes visible to Finance for processing

### 7.3 Expense Rejection Process
1. Manager authenticates and obtains access token
2. Manager views pending expenses via GET /api/expenses (filtered by department)
3. Manager selects expense and rejects via POST /api/expenses/{id}/reject
4. System validates EXPENSE_REJECT authority
5. System validates manager is tenant manager or department manager
6. System updates status to REJECTED
7. System records rejector identity and decision date
8. Audit log entry created with EXPENSE_REJECTED action

### 7.4 Expense Processing Process
1. Finance officer authenticates and obtains access token
2. Finance views approved expenses via GET /api/expenses (filtered to approved)
3. Finance selects expense and processes via POST /api/expenses/{id}/process
4. System validates EXPENSE_PROCESS authority
5. System validates expense status is APPROVED
6. System validates processor is in same tenant
7. System updates status to PROCESSED
8. System records processor identity and processing date
9. Audit log entry created with EXPENSE_PROCESSED action

### 7.5 Password Reset Process
1. User requests password reset via POST /api/auth/forgot-password with email
2. System validates rate limit (10 requests per 60-second window)
3. System generates single-use token with 15-minute expiry
4. System hashes token with SHA-256 and stores in database
5. System sends reset link to user's email
6. User clicks link and submits new password via POST /api/auth/reset-password
7. System validates token (exists, unused, unexpired)
8. System validates new password meets complexity requirements
9. System hashes new password with BCrypt and updates user record
10. System marks token as used
11. System revokes all refresh tokens for the user
12. Audit log entry created with PASSWORD_CHANGED action

### 7.6 MFA Setup Process
1. Authenticated user initiates MFA setup via POST /api/mfa/enable
2. System validates user does not already have MFA enabled
3. If TOTP: system generates secret and returns QR code URI
4. If EMAIL: system generates OTP code and sends via email
5. User verifies code via POST /api/mfa/verify-setup
6. System validates code
7. System enables MFA for the user
8. Audit log entry created with MFA_ENABLED action

---

## 8. Validation Rules

### Input Validation
- All @RequestBody parameters must be annotated with @Valid
- Username: @NotBlank, @Size(max=50), unique
- Email: @NotBlank, @Email, unique
- Password: @NotBlank, @Password (custom: 8+ chars, uppercase, lowercase, digit, special char)
- First/Last name: @NotBlank, @Size(max=100)
- Expense title: @NotBlank
- Expense amount: @NotNull, @Positive, @Digits(integer=15, fraction=4)
- Expense category: @NotBlank
- Tenant name: @NotBlank, @Size(max=100), unique
- Department name: @NotBlank, @Size(max=100), unique within tenant
- Role name: @NotBlank

### Business Validation
- User must belong to a tenant for expense operations
- Only PENDING expenses can be edited, cancelled, approved, or rejected
- Only APPROVED expenses can be processed
- Approver must be tenant manager or department manager
- Processor must have EXPENSE_PROCESS authority
- Privilege hierarchy must be satisfied for user management operations
- Last admin cannot be deleted or disabled
- Built-in roles cannot be modified or deleted
- Roles assigned to users cannot be deleted

### Authorization Validation
- Every API request must include valid JWT access token
- Method-level @PreAuthorize annotations enforce permission requirements
- Tenant isolation enforced at service layer
- Ownership checks enforced for expense modifications

---

## 9. Error Handling

### Authentication Failures
- Invalid credentials → 401 Unauthorized with "Invalid credentials" message
- No user enumeration (same message for wrong username and wrong password)
- Account locked → 429 Too Many Requests with lockout duration message

### Authorization Failures
- Insufficient permissions → 403 Forbidden with "Access denied: Insufficient permissions"
- Tenant isolation violation → 403 Forbidden
- Privilege hierarchy violation → 403 Forbidden

### Validation Failures
- Field validation errors → 400 Bad Request with field-level error details
- Constraint violations → 400 Bad Request with constraint details

### Resource Not Found
- Entity not found → 404 Not Found (when applicable, otherwise 400/403 to avoid information disclosure)

### Business Rule Violations
- Invalid status transition → 409 Conflict with descriptive message
- Duplicate username/email → 400 Bad Request with specific message
- Last admin protection → 400 Bad Request with specific message
- Built-in role modification → 400 Bad Request with specific message

### Unexpected Failures
- Internal server error → 500 Internal Server Error with "An unexpected error occurred"
- Stack traces never exposed to clients
- Internal details never logged to client response

---

## 10. External Integrations

### 10.1 Email Service (SMTP)
- **Purpose:** Deliver transactional emails for password reset, email verification, and MFA codes
- **Trigger:** Password reset request, MFA setup with EMAIL method, email verification request
- **Data Exchanged:**
  - Outbound: recipient email address, subject, body (password reset link, verification link, OTP code)
  - Inbound: None (fire-and-forget)
- **Expected Response:** Email sent successfully or failure logged
- **Failure Behavior:** Email sending failures are caught and logged as warnings; do not block the requesting operation

### 10.2 Redis
- **Purpose:** Token storage, rate limiting, caching, MFA session and OTP storage
- **Trigger:** Login, token refresh, logout, MFA verification, rate limit checks, cache operations
- **Data Exchanged:**
  - Refresh token hashes (SHA-256) with TTL
  - Rate limit counters with sliding window
  - MFA OTP codes with expiry
  - MFA pending session tokens with TTL
  - Cached data with configurable TTL
- **Expected Response:** Redis operations succeed or fail gracefully
- **Failure Behavior:** Redis unavailability affects token management, rate limiting, caching, and MFA flows

### 10.3 Prometheus/Micrometer
- **Purpose:** Application metrics collection and monitoring
- **Trigger:** Periodic scraping by Prometheus server
- **Data Exchanged:**
  - Outbound: health status, request metrics, JVM metrics, custom business metrics
  - Inbound: None
- **Expected Response:** Metrics endpoint returns current metrics
- **Failure Behavior:** Metrics endpoint unavailability does not affect application functionality

### 10.4 Database (PostgreSQL/H2)
- **Purpose:** Persistent storage for all business entities
- **Trigger:** All application operations
- **Data Exchanged:**
  - All CRUD operations on users, tenants, departments, roles, expenses, audit logs, password reset tokens
  - Schema migrations via Flyway
- **Expected Response:** Database operations succeed
- **Failure Behavior:** Database unavailability prevents all application functionality

---

## 11. Automated Processes

### 11.1 Password Reset Token Cleanup
- **Schedule:** Daily at midnight (cron: `0 0 0 * * ?`)
- **Action:** Deletes all expired or used password reset tokens from the database
- **Purpose:** Prevent unbounded growth of token records
- **Trigger:** Scheduled (automatic)

### 11.2 Account Lockout Expiration
- **Schedule:** Checked on each login attempt
- **Action:** If current time is past the lockout expiry, unlock the account
- **Purpose:** Automatically restore access after lockout period
- **Trigger:** Login attempt (on-demand)

---

## 12. Business Rules Catalog

| ID | Rule | Description |
|----|------|-------------|
| BR-001 | No Self-Registration | Users cannot register themselves; all accounts must be created by an authorized administrator. |
| BR-002 | Tenant Isolation | All data queries are scoped to the user's tenant unless the user is a super admin. |
| BR-003 | Expense Lifecycle | Expenses must follow status progression: PENDING → APPROVED/REJECTED/CANCELLED → PROCESSED. |
| BR-004 | Approval Authority | Only managers with EXPENSE_APPROVE permission can approve expenses. |
| BR-005 | Rejection Authority | Only managers with EXPENSE_REJECT permission can reject expenses. |
| BR-006 | Processing Authority | Only users with EXPENSE_PROCESS permission can process approved expenses. |
| BR-007 | Privilege Hierarchy | Users cannot manage other users with equal or higher permissions. |
| BR-008 | Last Admin Protection | The last user with ADMIN role in a tenant cannot be deleted or disabled. |
| BR-009 | Role Immutability | Built-in roles cannot be modified or deleted. |
| BR-010 | Role Assignment Restrictions | Only ADMIN can assign ADMIN or USER_MANAGER roles. |
| BR-011 | Expense Editing | Only PENDING expenses can be edited or cancelled. |
| BR-012 | Expense Ownership | Only the owner or authorized manager can edit PENDING expenses. |
| BR-013 | Password Complexity | Passwords must be at least 8 characters with uppercase, lowercase, digit, and special character. |
| BR-014 | Account Lockout | Accounts are locked after 5 consecutive failed login attempts for 15 minutes. |
| BR-015 | Token Rotation | Refresh tokens are rotated on each use; old tokens are revoked. |
| BR-016 | MFA Rate Limiting | MFA verification codes are rate-limited to 10 attempts per 60-second window. |
| BR-017 | Reset Token Expiry | Password reset tokens expire after 15 minutes and are single-use. |
| BR-018 | User Manager Scope | User managers can only create and manage users within their own tenant. |
| BR-019 | Department Uniqueness | Department names must be unique within a tenant. |
| BR-020 | Tenant Uniqueness | Tenant names must be unique across the system. |

---

## 13. Functional Requirements Catalog

| ID | Requirement | Actor | Preconditions | Expected Behavior | Business Rules |
|----|-------------|-------|---------------|-------------------|----------------|
| FR-001 | User login with credentials | Unauthenticated User | Account exists and is enabled | System validates credentials and issues JWT tokens or MFA challenge | BR-014 |
| FR-002 | Token refresh | Authenticated User | Valid refresh token exists | System issues new access/refresh token pair | BR-015 |
| FR-003 | User logout | Authenticated User | User is authenticated | System revokes all refresh tokens | - |
| FR-004 | Get current user profile | Authenticated User | User is authenticated | System returns user profile | - |
| FR-005 | Create user | ADMIN, USER_MANAGER | USER_CREATE permission; unique username/email | System creates user with tenant assignment | BR-001, BR-018 |
| FR-006 | Update user | ADMIN, USER_MANAGER | USER_WRITE permission; privilege hierarchy satisfied | System updates user details | BR-007 |
| FR-007 | Delete user | ADMIN, USER_MANAGER | USER_DELETE permission; not last admin; not self | System deletes user | BR-007, BR-008 |
| FR-008 | Enable/disable user | ADMIN, USER_MANAGER | USER_ENABLE permission; not last admin | System toggles user enabled status | BR-008 |
| FR-009 | Assign role to user | ADMIN, USER_MANAGER | USER_ASSIGN_ROLE permission; role exists | System assigns role to user | BR-010, BR-007 |
| FR-010 | Remove role from user | ADMIN, USER_MANAGER | USER_ASSIGN_ROLE permission; not last admin role | System removes role from user | BR-008 |
| FR-011 | List users | ADMIN, USER_MANAGER, MANAGER, AUDITOR | USER_READ permission | System returns paginated user list (tenant-scoped) | BR-002 |
| FR-012 | Get user by ID | ADMIN, USER_MANAGER, MANAGER, AUDITOR | USER_READ permission | System returns user details (tenant-scoped) | BR-002 |
| FR-013 | Create tenant | ADMIN | TENANT_CREATE permission; unique name | System creates tenant | - |
| FR-014 | Update tenant | ADMIN | TENANT_UPDATE permission | System updates tenant details | - |
| FR-015 | Delete tenant | ADMIN | TENANT_DELETE permission | System deletes tenant | - |
| FR-016 | List tenants | ADMIN | TENANT_READ permission | System returns paginated tenant list | - |
| FR-017 | Get tenant by ID | ADMIN | TENANT_READ permission | System returns tenant details | - |
| FR-018 | Create department | ADMIN, USER_MANAGER | DEPARTMENT_CREATE permission; unique name in tenant | System creates department | BR-019 |
| FR-019 | Update department | ADMIN, USER_MANAGER | DEPARTMENT_UPDATE permission | System updates department | BR-019 |
| FR-020 | Delete department | ADMIN, USER_MANAGER | DEPARTMENT_DELETE permission | System deletes department | - |
| FR-021 | List departments | ADMIN, USER_MANAGER, MANAGER, AUDITOR | DEPARTMENT_READ permission | System returns paginated department list (tenant-scoped) | BR-002 |
| FR-022 | Create custom role | ADMIN | ROLE_WRITE permission | System creates custom role | BR-009 |
| FR-023 | Update custom role | ADMIN | ROLE_WRITE permission; role is custom | System updates custom role | BR-009 |
| FR-024 | Delete custom role | ADMIN | ROLE_DELETE permission; role is custom; not assigned to users | System deletes custom role | BR-009 |
| FR-025 | Add permission to role | ADMIN | ROLE_ASSIGN_PERMISSION permission; role is custom | System adds permission to role | BR-009 |
| FR-026 | Remove permission from role | ADMIN | ROLE_ASSIGN_PERMISSION permission; role is custom | System removes permission from role | BR-009 |
| FR-027 | List roles | ADMIN, USER_MANAGER, MANAGER | ROLE_READ permission | System returns paginated role list | - |
| FR-028 | Create expense | Any user with EXPENSE_CREATE | User belongs to a tenant | System creates expense with PENDING status | BR-003, BR-011 |
| FR-029 | Update expense | Owner, Manager with EXPENSE_UPDATE | EXPENSE_UPDATE permission; expense is PENDING; privilege hierarchy satisfied | System updates expense | BR-003, BR-011, BR-012 |
| FR-030 | Cancel expense | Owner, Manager with EXPENSE_UPDATE | EXPENSE_UPDATE permission; expense is PENDING | System cancels expense | BR-003, BR-011, BR-012 |
| FR-031 | Approve expense | Manager with EXPENSE_APPROVE | EXPENSE_APPROVE permission; expense is PENDING; tenant/department manager | System approves expense | BR-003, BR-004 |
| FR-032 | Reject expense | Manager with EXPENSE_REJECT | EXPENSE_REJECT permission; expense is PENDING; tenant/department manager | System rejects expense | BR-003, BR-005 |
| FR-033 | Process expense | Finance with EXPENSE_PROCESS | EXPENSE_PROCESS permission; expense is APPROVED; same tenant | System processes expense | BR-003, BR-006 |
| FR-034 | List expenses | Various | EXPENSE_READ permission; role determines scope | System returns paginated expense list | BR-002 |
| FR-035 | Get expense by ID | Various | EXPENSE_READ permission; authorization check | System returns expense details | BR-002 |
| FR-036 | View audit logs | ADMIN, AUDITOR | AUDIT_LOG_READ permission | System returns paginated audit log list (tenant-scoped) | BR-002 |
| FR-037 | Change password | Authenticated User | User is authenticated; current password verified | System updates password; revokes all tokens | BR-013, BR-015 |
| FR-038 | Request password reset | Unauthenticated User | Rate limit not exceeded | System sends reset email | BR-017 |
| FR-039 | Reset password with token | Unauthenticated User | Valid, unused, unexpired token | System updates password; revokes all tokens | BR-013, BR-017 |
| FR-040 | Enable MFA | Authenticated User | MFA not already enabled | System initiates MFA setup | - |
| FR-041 | Verify MFA setup | Authenticated User | MFA setup initiated | System activates MFA | - |
| FR-042 | Disable MFA | Authenticated User | MFA enabled; current password verified | System disables MFA | - |
| FR-043 | Get MFA status | Authenticated User | User is authenticated | System returns MFA configuration status | - |
| FR-044 | Verify MFA during login | Unauthenticated User | MFA session token provided | System verifies OTP code | BR-016 |

---

## 14. Traceability

### Requirements to Functional Modules
| Requirement | Module |
|-------------|--------|
| FR-001 to FR-004 | Authentication Module |
| FR-005 to FR-012 | User Management Module |
| FR-013 to FR-017 | Tenant Management Module |
| FR-018 to FR-021 | Department Management Module |
| FR-022 to FR-027 | Role Management Module |
| FR-028 to FR-035 | Expense Management Module |
| FR-036 | Audit Logging Module |
| FR-037 to FR-039 | Password Management Module |
| FR-040 to FR-044 | Multi-Factor Authentication Module |

### Requirements to Business Rules
| Requirement | Business Rules |
|-------------|---------------|
| FR-001 | BR-014 (Account Lockout) |
| FR-002 | BR-015 (Token Rotation) |
| FR-005 | BR-001, BR-018 |
| FR-006, FR-007, FR-009, FR-010 | BR-007 (Privilege Hierarchy) |
| FR-007, FR-008 | BR-008 (Last Admin Protection) |
| FR-009, FR-022 to FR-027 | BR-009, BR-010 |
| FR-028 to FR-035 | BR-002, BR-003, BR-004, BR-005, BR-006, BR-011, BR-012 |
| FR-037, FR-039 | BR-013 (Password Complexity) |
| FR-038 | BR-017 (Reset Token Expiry) |
| FR-044 | BR-016 (MFA Rate Limiting) |

### Requirements to Test Coverage
| Requirement | Test Class |
|-------------|-----------|
| FR-001 to FR-004 | SecurityIntegrationTest, AuthServiceTest |
| FR-005 to FR-012 | UserManagementControllerIntegrationTest |
| FR-022 to FR-027 | RoleManagementControllerIntegrationTest |
| FR-028 to FR-035 | ExpenseControllerIntegrationTest, ExpenseServiceTest |
| FR-040 to FR-044 | MfaServiceTest |
| FR-001 (lockout) | RateLimitingServiceTest |
| FR-034, FR-035 (authorization) | AuthorizationServiceTest |
| FR-002 (token hashing) | TokenHashingServiceTest, JwtTokenProviderTest |

---

## 15. Known Gaps

### Unimplemented Functionality
1. **Email Verification:** An email verification endpoint exists but the full verification flow is not wired into the user creation process.
2. **Reporting Endpoints:** The REPORT_READ permission is defined and assigned to multiple roles, but no reporting API endpoints are implemented.
3. **File Attachments:** No support for attaching files or documents to expenses.
4. **External SSO Integration:** No integration with external identity providers (e.g., OAuth2, SAML).
5. **Payment Gateway Integration:** Expense processing marks expenses as PROCESSED but does not execute actual financial transactions.
6. **Tenant-Level MFA Policy:** MFA is opt-in per user; no tenant-level MFA requirement is enforced.
7. **Frontend:** No frontend user interface is included; this is an API-only backend.
8. **Expense Resubmission:** Rejected expenses cannot be resubmitted; the workflow does not support returning to PENDING status.
9. **Batch Operations:** No batch processing for expenses (e.g., bulk approve, bulk process).
10. **Notification Preferences:** No user-configurable notification preferences for email notifications.

### Areas Requiring Business Confirmation
1. Whether the USER role is actively used or retained solely for backward compatibility.
2. Whether tenant status (INACTIVE/SUSPENDED) should restrict user login or data access.
3. Whether department manager assignment should affect expense visibility beyond the current model.
4. Specific business justification for each of the 26 permissions.
5. Whether additional expense statuses or workflow transitions are planned.
6. Data retention policies for audit logs and expense records.
7. Whether the expense category field has a controlled vocabulary or is free-text.
8. Whether multi-currency support is planned (amount field is a single BigDecimal without currency).
