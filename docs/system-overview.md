# System Overview

## 1. Purpose

The system is a **multi-tenant expense management application** that enables organizations to submit, approve, and process business expenses.

**Why it exists:**
- Provides a structured, auditable workflow for employee expense submissions
- Enforces organizational approval hierarchies and spending controls
- Supports multi-tenant isolation so multiple organizations can use the same platform independently
- Maintains a complete audit trail for compliance and reporting

**Primary objectives:**
- Automate the expense lifecycle from submission through payment processing
- Enforce role-based access control and multi-tenant data isolation
- Provide secure authentication with multi-factor authentication (MFA) support
- Deliver comprehensive audit logging for regulatory and internal compliance

---

## 2. Scope

**The application is responsible for:**
- User authentication, authorization, and account management
- Multi-tenant organization and department management
- Expense submission, approval, rejection, cancellation, and payment processing
- Role-based access control with granular permissions
- Audit logging of all security and business events
- Password management including reset flows
- Multi-factor authentication (TOTP and email OTP)

**The application is NOT responsible for:**
- Payment gateway integration (expense processing marks expenses as PROCESSED but does not execute actual financial transactions)
- Document/file attachment storage (not implemented in current codebase)
- Email delivery infrastructure (uses SMTP configuration but relies on an external mail server)
- Frontend user interface (API-only backend)
- Reporting and analytics dashboards (REPORT_READ permission exists but no reporting endpoints are implemented)
- Integration with external accounting or ERP systems

**Major boundaries:**
- The system operates as a REST API backend
- All tenant data is logically isolated via tenant ID filtering
- Authentication is stateless via JWT tokens
- Session management is handled via Redis-backed refresh tokens

---

## 3. Target Users

| User Type | Who They Are | Why They Use the System | What They Can Do |
|-----------|--------------|------------------------|------------------|
| **System Administrator (Super Admin)** | Platform operator with no tenant assignment | Manage the entire platform, all tenants, and all users across organizations | Full CRUD on tenants, users, roles, departments; view all expenses and audit logs |
| **Tenant Administrator** | Organization-level admin within a specific tenant | Manage their organization's users, departments, and settings | Create/edit users and departments within their tenant; assign roles; view tenant expenses |
| **User Manager** | HR or team lead responsible for user administration within a tenant | Create and manage user accounts for their organization | Create users, update user details, assign roles (except ADMIN and USER_MANAGER) |
| **Manager** | Department manager who supervises expense approvals | Review and approve/reject employee expense submissions | View department expenses, approve or reject pending expenses |
| **Employee** | Regular staff member submitting business expenses | Submit expenses for reimbursement and track their status | Create, view, edit, and cancel own expenses |
| **Finance Officer** | Finance department staff responsible for payment processing | Process approved expenses for payment | View approved expenses, mark them as processed |
| **Auditor** | Compliance or internal audit personnel | Review expenses and audit logs for compliance | Read-only access to all tenant expenses, users, departments, and audit logs |

---

## 4. Roles and Responsibilities

| Role | Business Purpose | Key Capabilities |
|------|-----------------|------------------|
| **ADMIN** | Full system administrator with unrestricted access across all tenants | Manage all tenants, users, roles, departments; view all expenses; view all audit logs |
| **USER_MANAGER** | Organization-level user administrator | Create and manage users within their tenant; assign most roles |
| **MANAGER** | Department-level expense approver | Review and approve/reject expenses within their department |
| **EMPLOYEE** | Expense submitter | Create, edit, cancel, and view own expenses |
| **AUDITOR** | Read-only compliance reviewer | View all tenant data including expenses, users, departments, and audit logs |
| **FINANCE** | Payment processor | Process approved expenses for payment |
| **USER** | Legacy low-privilege role | Minimal permissions; retained for backward compatibility |

---

## 5. Major Functionalities

### 5.1 Authentication and Session Management
- **What:** User login with JWT-based stateless authentication
- **Who:** All users
- **Why:** Secure access to the system
- **Key rules:** Access tokens expire in 15 minutes; refresh tokens in 7 days; tokens rotated on each refresh; all tokens revoked on logout or password change
- **Dependencies:** Redis for token storage, email service for MFA codes

### 5.2 Multi-Factor Authentication (MFA)
- **What:** Additional security layer requiring a second verification factor
- **Who:** Any authenticated user can enable; required per organizational policy (not determined from code)
- **Why:** Strengthen account security
- **Key rules:** Supports TOTP (authenticator apps) and EMAIL (one-time code); MFA setup requires code verification before activation; accounts locked after 5 failed MFA attempts
- **Dependencies:** Email service for EMAIL method; authenticator app for TOTP

### 5.3 User Management
- **What:** CRUD operations on user accounts with role assignment
- **Who:** ADMIN, USER_MANAGER
- **Why:** Maintain the organization's workforce in the system
- **Key rules:** No self-registration; user manager can only manage users in their own tenant; cannot delete last admin; cannot modify users with higher privileges
- **Dependencies:** Role management, tenant management

### 5.4 Tenant Management
- **What:** CRUD operations on tenant (organization) records
- **Who:** ADMIN (super admin)
- **Why:** Onboard and manage organizations on the platform
- **Key rules:** Each tenant has a unique name and status (ACTIVE/INACTIVE/SUSPENDED)
- **Dependencies:** None (top-level entity)

### 5.5 Department Management
- **What:** CRUD operations on departments within a tenant
- **Who:** ADMIN, USER_MANAGER (within their tenant)
- **Why:** Organize users into functional units for expense routing and approval
- **Key rules:** Departments are scoped to a tenant; each department has a manager; unique name per tenant
- **Dependencies:** Tenant management, user management

### 5.6 Role and Permission Management
- **What:** Define and manage roles with associated permissions
- **Who:** ADMIN
- **Why:** Control what users can do in the system
- **Key rules:** 7 built-in roles are immutable; custom roles can be created; roles assigned to users cannot be deleted; users cannot grant permissions they don't have
- **Dependencies:** Permission definitions

### 5.7 Expense Submission
- **What:** Employees create expense requests with title, description, amount, and category
- **Who:** EMPLOYEE, MANAGER, ADMIN, or any user with EXPENSE_CREATE permission
- **Why:** Initiate the reimbursement process
- **Key rules:** User must belong to a tenant; department defaults to user's department; status starts as PENDING
- **Dependencies:** Tenant and department assignment

### 5.8 Expense Approval Workflow
- **What:** Managerial review and decision on pending expenses
- **Who:** MANAGER, ADMIN (with EXPENSE_APPROVE/EXPENSE_REJECT permissions)
- **Why:** Ensure expenses comply with organizational policies before payment
- **Key rules:** Only PENDING expenses can be approved/rejected; approver must be a tenant manager or department manager; records approver identity and decision date
- **Dependencies:** Expense submission

### 5.9 Expense Payment Processing
- **What:** Finance marks approved expenses as processed for payment
- **Who:** FINANCE, ADMIN (with EXPENSE_PROCESS permission)
- **Why:** Complete the expense reimbursement lifecycle
- **Key rules:** Only APPROVED expenses can be processed; processor must be in the same tenant; records processor identity and processing date
- **Dependencies:** Expense approval

### 5.10 Audit Logging
- **What:** Persistent record of all security and business events
- **Who:** AUDITOR, ADMIN (viewing); system (writing)
- **Why:** Compliance, troubleshooting, and security monitoring
- **Key rules:** Each log entry includes actor, tenant, action, resource type/id, details, and timestamp; super admins see all logs; others see only their tenant's logs
- **Dependencies:** All system operations

### 5.11 Password Management
- **What:** Password change, reset, and policy enforcement
- **Who:** All authenticated users (change); unauthenticated users (reset via email)
- **Why:** Account security and self-service recovery
- **Key rules:** Minimum 8 characters with complexity requirements; BCrypt hashing; current password required for change; reset tokens expire in 15 minutes and are single-use
- **Dependencies:** Email service for reset links

### 5.12 Account Security
- **What:** Account lockout, rate limiting, and security headers
- **Who:** System (enforcement); all users (affected)
- **Why:** Protect against brute force and unauthorized access
- **Key rules:** 5 failed attempts trigger 15-minute lockout; rate limiting on sensitive endpoints; HTTP security headers enforced
- **Dependencies:** Redis for rate limiting counters

---

## 6. Typical User Workflows

### 6.1 User Login (without MFA)
```
User → Enter credentials → System validates → Account locked? → No → Issue JWT tokens → Return tokens → User accesses API
```

### 6.2 User Login (with MFA enabled)
```
User → Enter credentials → System validates → MFA enabled? → Yes → Return MFA session token → User enters OTP code → System verifies → Issue JWT tokens → User accesses API
```

### 6.3 Expense Submission
```
Employee → Create expense (title, amount, category) → System validates → Assign tenant and department → Save as PENDING → Audit log created
```

### 6.4 Expense Approval
```
Manager → View pending expenses → Select expense → Approve → System validates authority → Update status to APPROVED → Record approver and date → Audit log created
```

### 6.5 Expense Processing (Payment)
```
Finance → View approved expenses → Select expense → Process → System validates authority → Update status to PROCESSED → Record processor and date → Audit log created
```

### 6.6 User Creation
```
Admin/User Manager → Create user (username, email, name, role) → System validates uniqueness and authority → Create user with tenant assignment → Audit log created
```

### 6.7 Password Reset
```
User → Forgot password → Enter email → System sends reset link (rate-limited) → User clicks link → Enter new password → System validates token and password → Update password → Revoke all refresh tokens → Audit log created
```

---

## 7. Authentication and Access Control Overview

### Authentication
- Users authenticate using a username (or email) and password
- Upon successful verification, the system issues a short-lived JWT access token (15 minutes) and a longer-lived refresh token (7 days)
- If multi-factor authentication is enabled, the user must also provide a one-time code after password verification
- Refresh tokens are stored securely as SHA-256 hashes in Redis and are rotated on each use
- All tokens are revoked on logout or password change

### Authorization
- Every API request requires a valid JWT access token
- The system checks the user's roles and permissions for each operation
- Method-level security annotations enforce permission requirements (e.g., `EXPENSE_APPROVE` for approving expenses)
- Tenant isolation ensures users can only access data within their own organization (unless they are a super admin)
- Ownership checks ensure employees can only modify their own expenses
- Privilege hierarchy prevents users from managing others with equal or higher permissions

### Major Security Boundaries
- Unauthenticated users can only access login, token refresh, and password reset endpoints
- All other endpoints require authentication
- Sensitive operations (user management, expense approval, audit log access) require specific permissions
- Cross-tenant data access is blocked at the service layer

---

## 8. External Systems and Integrations

| System | Purpose | Information Exchanged | Direction | When Used |
|--------|---------|----------------------|-----------|-----------|
| **Email Server (SMTP)** | Deliver transactional emails | Password reset links, email verification links, MFA OTP codes | Outbound | Password reset, email verification, MFA via EMAIL method |
| **Redis** | Token storage, rate limiting, caching, MFA session storage | Refresh token hashes, rate limit counters, OTP codes, MFA session tokens, cached data | Bidirectional | Login, refresh, logout, MFA verification, all cached operations |
| **Prometheus/Micrometer** | Application metrics and monitoring | Health status, request metrics, JVM metrics | Outbound | Exposed via actuator endpoints for scraping |
| **Database (PostgreSQL/H2)** | Persistent data storage | All business entities (users, tenants, expenses, audit logs, etc.) | Bidirectional | All application operations |

---

## 9. Data Overview

### Tenants
Organizations that use the system. Each tenant represents a separate company or business unit. All data within the system is isolated by tenant.

### Users
Individuals who have accounts in the system. Each user belongs to one tenant and may belong to one department. Users have roles that determine their permissions.

### Roles
Named collections of permissions. The system has 7 built-in roles (ADMIN, USER_MANAGER, MANAGER, EMPLOYEE, AUDITOR, FINANCE, USER) that cannot be modified. Custom roles can be created by administrators.

### Permissions
26 granular access rights (e.g., EXPENSE_CREATE, USER_READ, TENANT_UPDATE) that control what operations a user can perform.

### Departments
Organizational units within a tenant. Each department has a name (unique within the tenant) and a designated manager. Departments group users and are used for expense routing and approval.

### Expenses
Business expense requests submitted by employees. Each expense has a title, description, amount, category, and status. Expenses follow a lifecycle: PENDING → APPROVED/REJECTED/CANCELLED → PROCESSED.

### Audit Logs
Immutable records of all significant system events. Each log entry captures who performed an action, what was done, when it occurred, and which tenant and resource were affected.

### Refresh Tokens
Secure references to active login sessions. Stored as one-way hashes in Redis with expiration times.

### Password Reset Tokens
Single-use tokens for password recovery. Stored as one-way hashes with 15-minute expiration.

---

## 10. Notifications and Background Processing

### Scheduled Processes
- **Password Reset Token Cleanup:** A daily scheduled job (runs at midnight) deletes expired or used password reset tokens from the database.

### Background Processing
- No asynchronous background jobs are defined in the current implementation.
- Email sending is synchronous (occurs during the request lifecycle).

### Notifications
- Password reset emails are sent when a user requests a password reset.
- MFA OTP codes are sent via email when the EMAIL MFA method is used.
- Email sending failures are logged as warnings but do not block the requesting operation.

---

## 11. Business Rules

1. **No Self-Registration:** Users cannot register themselves; all accounts must be created by an administrator or user manager.
2. **Tenant Isolation:** All data queries are scoped to the user's tenant unless they are a super admin (ADMIN role with no tenant assignment).
3. **Expense Lifecycle:** Expenses must follow the status progression: PENDING → APPROVED/REJECTED/CANCELLED → PROCESSED. Status cannot be skipped or reversed.
4. **Approval Authority:** Only managers (tenant or department level) with EXPENSE_APPROVE permission can approve expenses.
5. **Processing Authority:** Only users with EXPENSE_PROCESS permission (FINANCE role) can process approved expenses for payment.
6. **Privilege Hierarchy:** Users cannot manage other users who have equal or higher permissions.
7. **Last Admin Protection:** The last user with the ADMIN role in a tenant cannot be deleted or disabled.
8. **Role Immutability:** Built-in roles (ADMIN, USER, USER_MANAGER, MANAGER, EMPLOYEE, AUDITOR, USER) cannot be modified or deleted.
9. **Role Assignment Restrictions:** Only administrators can assign the ADMIN or USER_MANAGER roles. Users cannot grant permissions they do not possess.
10. **Expense Editing:** Only PENDING expenses can be edited or cancelled, and only by the owner or an authorized manager.
11. **Password Complexity:** Passwords must be at least 8 characters and include uppercase, lowercase, digit, and special characters.
12. **Account Lockout:** Accounts are locked after 5 consecutive failed login attempts for 15 minutes.
13. **Token Rotation:** Refresh tokens are rotated on each use; old tokens are revoked.
14. **MFA Verification:** MFA codes are rate-limited to 10 attempts per 60-second window per user.

---

## 12. System Boundaries

### Inside the Application
- User authentication and JWT token management
- Role and permission-based authorization
- Multi-tenant data isolation
- Expense CRUD and workflow management
- User, tenant, department, and role management
- Audit log recording and retrieval
- Password management and reset flows
- MFA setup and verification
- Rate limiting and account lockout
- Input validation and error handling

### Outside the Application
- Frontend user interface (separate application)
- Email delivery infrastructure (SMTP server)
- Payment processing systems (actual fund transfers)
- External accounting or ERP integrations
- Identity providers (no external SSO integration in current implementation)
- File storage systems (no document attachment support)

### Through External Integrations
- **Email Server:** Outbound email delivery for password resets, verification, and MFA codes
- **Redis:** Token storage, rate limiting, caching, and session management
- **Prometheus:** Metrics collection and monitoring

---

## 13. Important Constraints

### Security Constraints
- JWT secret key must be at least 32 characters (64+ bytes recommended for HS512)
- All passwords must meet complexity requirements (8+ chars, mixed case, digit, special char)
- Account lockout enforced after 5 failed attempts
- Rate limiting applied to sensitive endpoints
- HTTP security headers enforced (CSP, HSTS, X-Frame-Options, etc.)
- CORS restricted to configured frontend URL
- No sensitive data (passwords, tokens, secrets) is logged

### User Constraints
- Users must belong to a tenant (except super admins)
- Users can have multiple roles
- Users cannot modify accounts with higher privileges
- The last admin in a tenant cannot be removed

### Data Constraints
- Tenant names must be unique
- Department names must be unique within a tenant
- Usernames and emails must be unique across the system
- Expense amounts must be positive with up to 4 decimal places
- All list endpoints use pagination (configurable page size)

### Integration Constraints
- Email delivery depends on SMTP server availability
- Redis availability required for token storage, rate limiting, and caching
- Database required for persistent storage (H2 for dev/test, PostgreSQL for production)
- Password reset tokens expire after 15 minutes
- Refresh tokens expire after 7 days

### Operational Constraints
- `ddl-auto=validate` in dev/test environments; `none` in production
- Database migrations managed via Flyway
- No hot-reload of security configuration
- Audit logs are append-only (no update or delete operations exposed)

---

## 14. Glossary

| Term | Definition |
|------|-----------|
| **Tenant** | An organization or business unit that uses the system. All data within a tenant is isolated from other tenants. |
| **Super Admin** | A user with the ADMIN role and no tenant assignment. Has unrestricted cross-tenant access. |
| **Tenant Admin** | A user with the ADMIN role assigned to a specific tenant. Scoped to their tenant's data. |
| **JWT** | JSON Web Token — a compact, URL-safe token used for stateless authentication. Contains user identity and permission claims. |
| **Access Token** | A short-lived JWT (15 minutes) used to authenticate API requests. |
| **Refresh Token** | A longer-lived token (7 days) used to obtain new access tokens without re-authentication. |
| **MFA** | Multi-Factor Authentication — an additional security layer requiring a second verification factor beyond password. |
| **TOTP** | Time-based One-Time Password — an MFA method using authenticator apps like Google Authenticator. |
| **OTP** | One-Time Password — a single-use code for authentication. |
| **Authority** | A specific permission (e.g., EXPENSE_CREATE) that grants the ability to perform an operation. |
| **Role** | A named collection of permissions assigned to users (e.g., ADMIN, EMPLOYEE, MANAGER). |
| **Expense Lifecycle** | The progression of an expense through statuses: PENDING → APPROVED/REJECTED/CANCELLED → PROCESSED. |
| **Rate Limiting** | Controlling the number of requests a user can make within a time window to prevent abuse. |
| **Account Lockout** | Temporarily disabling an account after multiple failed login attempts. |
| **Audit Log** | A permanent record of who did what, when, and on which resource. |
| **Flyway** | A database migration tool that manages schema version control. |
| **BCrypt** | A password hashing algorithm used to securely store passwords. |
| **SHA-256** | A cryptographic hash function used for token hashing (refresh tokens, password reset tokens). |

---

## 15. Document Accuracy

### Documentation Notes

**Confidently confirmed from implementation:**
- All REST endpoints, their paths, HTTP methods, and required authorities
- All entity relationships and data model
- All 7 built-in roles and their permission sets
- Complete expense lifecycle (PENDING → APPROVED/REJECTED/CANCELLED → PROCESSED)
- JWT authentication flow including access/refresh token rotation
- MFA setup and verification flow (TOTP and EMAIL methods)
- Account lockout after 5 failed attempts
- Rate limiting on sensitive endpoints
- Password complexity requirements and hashing (BCrypt strength 12)
- Password reset flow with 15-minute token expiry
- Tenant isolation across all data queries
- Audit logging for security and business events
- Scheduled cleanup of expired password reset tokens (daily at midnight)
- Redis usage for token storage, rate limiting, caching, and MFA sessions
- Email integration for password reset, verification, and MFA codes
- Prometheus/Micrometer metrics exposure
- Privilege hierarchy enforcement (users cannot manage higher-privileged users)
- Last admin protection (cannot delete or disable)

**Areas where behavior was ambiguous:**
- Whether email verification is fully implemented (endpoint exists but verification flow is not fully wired in current codebase)
- Whether the USER role is actively used or retained solely for backward compatibility
- Whether the REPORT_READ permission is utilized (permission exists but no reporting endpoints are implemented)
- Organization-wide MFA policy enforcement (individual users can enable/disable MFA, but no tenant-level MFA requirement is enforced in the current implementation)

**Areas that require confirmation from business stakeholders:**
- Specific business justification for each of the 26 permissions
- Whether additional expense statuses or workflow transitions are planned
- Whether file attachment support for expenses is planned
- Whether integration with external accounting/payment systems is planned
- Specific email templates and content for transactional emails
- Whether reporting/dashboard functionality is planned for the REPORT_READ permission
- Tenant lifecycle management (what happens when a tenant is SUSPENDED or INACTIVE)
- Data retention policies for audit logs and expense records
