# API Documentation

## 1. API Overview

The application exposes a **RESTful API** for a multi-tenant expense management system. All endpoints return responses wrapped in a standard `ApiResponse<T>` envelope.

**Base URL**: Configured via `app.base-url` property (default: `http://localhost:8080`).

**Authentication**: JWT Bearer tokens via `Authorization: Bearer <token>` header.

**Content Type**: `application/json` for all request and response bodies.

**API Versioning**: No explicit versioning. Single version.

### Common Response Envelope

```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "errors": null
}
```

**Error response:**
```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "errors": {
    "fieldName": "error message"
  }
}
```

## 2. Authentication

### Login Flow

1. **Authenticate**: `POST /api/auth/login` with credentials.
2. **MFA** (if enabled): Use the `mfaSessionToken` from the login response to call `POST /api/auth/mfa/verify`.
3. **Use Token**: Include the `accessToken` in the `Authorization: Bearer <token>` header for subsequent requests. The `refreshToken` is automatically stored in an HTTP-only cookie.
4. **Refresh**: When the access token expires, call `POST /api/auth/refresh` — the refresh token is sent automatically via cookie.
5. **Logout**: Call `POST /api/auth/logout` to revoke the access token (blacklisted) and all refresh tokens, and clear the cookie.

### Required Headers

| Header | Value | Required |
|--------|-------|----------|
| `Authorization` | `Bearer <accessToken>` | Yes (except public endpoints) |
| `Content-Type` | `application/json` | Yes (for request bodies) |

### Token Expiration

| Token | Expiration | Storage |
|-------|------------|---------|
| Access Token | 15 minutes | Client-side (memory/localStorage) |
| Refresh Token | 7 days | HTTP-only, Secure, SameSite=Strict cookie |
| MFA Pending Token | 5 minutes | Client-side (temporary) |

## 3. Authorization

### Authorization Matrix

| Functionality | PLATFORM_ADMIN | TENANT_ADMIN | USER_MANAGER | DEPARTMENT_MANAGER | EMPLOYEE | AUDITOR | FINANCE |
|---------------|-------|--------------|---------|----------|---------|---------|------|
| **Auth** | | | | | | | |
| Login | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Refresh Token | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Change Password | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| **MFA** | | | | | | | |
| Enable/Disable MFA | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| **Expenses** | | | | | | | |
| View Own Expenses | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Create Expense | Yes | Yes | Yes | Yes | No | No | No |
| Edit Own Expense | Yes | Yes | Yes | Yes | No | No | No |
| Cancel Own Expense | Yes | Yes | Yes | Yes | No | No | No |
| View Tenant Expenses | Yes | Yes | Yes | No | Yes | Yes | No |
| Approve/Reject | Yes | Yes | Yes (dept) | No | No | No | No |
| Process (Finance) | Yes | No | No | No | No | Yes | No |
| **User Management** | | | | | | | |
| List Users | Yes | Yes | Yes | No | Yes | No | No |
| Create User | Yes | Yes | No | No | No | No | No |
| Update User | Yes | Yes | No | No | No | No | No |
| Delete User | Yes | No | No | No | No | No | No |
| Enable/Disable User | Yes | Yes | No | No | No | No | No |
| Assign/Remove Role | Yes | Yes | No | No | No | No | No |
| **Role Management** | | | | | | | |
| List Roles | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Create/Update/Delete Role | Yes | No | No | No | No | No | No |
| Manage Permissions | Yes | No | No | No | No | No | No |
| **Tenant Management** | | | | | | | |
| List Tenants | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Create/Update/Delete Tenant | Yes | No | No | No | No | No | No |
| **Department Management** | | | | | | | |
| List Departments | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Create/Update/Delete Dept | Yes | Yes | No | No | No | No | No |
| **Audit Logs** | | | | | | | |
| View Audit Logs | Yes | No | No | No | Yes | No | No |

## 4. Common Conventions

### HTTP Methods
| Method | Purpose |
|--------|---------|
| `GET` | Retrieve resources |
| `POST` | Create resources or perform actions |
| `PUT` | Update resources |
| `DELETE` | Remove resources |

### Status Codes
| Code | Meaning |
|------|---------|
| `200` | Success |
| `400` | Validation error or bad request |
| `401` | Invalid or missing authentication |
| `403` | Insufficient permissions |
| `404` | Resource not found |
| `409` | Conflict (e.g., duplicate name) |
| `429` | Rate limit exceeded or account locked |
| `500` | Internal server error |

### Pagination
All list endpoints support pagination via query parameters:
- `page` (default: 0)
- `size` (default: 20)
- `sort` (e.g., `sort=createdAt,desc`)

**Paginated response format:**
```json
{
  "content": [ ... ],
  "pageable": { ... },
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

### Validation
- All request bodies with `@Valid @RequestBody` are validated server-side.
- Validation errors return `400 Bad Request` with field-level error messages in the `errors` map.

## 5. Endpoint Documentation

---

### Authentication

---

#### POST /api/auth/login

**Purpose**: Authenticate a user and receive tokens or an MFA challenge.

**Authentication**: Not required.

**Request Body:**
```json
{
  "usernameOrEmail": "string (required)",
  "password": "string (required)"
}
```

**Response (no MFA):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "username": "john.doe",
    "roles": ["EMPLOYEE"]
  }
}
```

**Note:** The `refreshToken` is set as an HTTP-only, Secure, SameSite=Strict cookie named `refresh_token`. It is not included in the response body.
```

**Response (MFA required):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "mfaRequired": true,
    "mfaSessionToken": "abc123...",
    "method": "TOTP",
    "expiresIn": 300
  }
}
```

**Errors:**
- `400`: Validation failed
- `401`: Invalid credentials
- `429`: Account locked due to too many failed attempts

---

#### POST /api/auth/mfa/verify

**Purpose**: Complete MFA verification after initial login.

**Authentication**: Not required (uses `mfaSessionToken`).

**Request Body:**
```json
{
  "mfaSessionToken": "string (required)",
  "code": "string (required)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "MFA verification successful",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "username": "john.doe",
    "roles": ["EMPLOYEE"]
  }
}
```

**Note:** The `refreshToken` is set as an HTTP-only cookie. It is not included in the response body.
```

**Errors:**
- `400`: Validation failed
- `401`: Invalid MFA code or expired session token
- `429`: Too many MFA verification attempts

---

#### POST /api/auth/refresh

**Purpose**: Exchange the refresh token (from cookie) for a new access token and refresh token.

**Authentication**: Not required (refresh token is sent via HTTP-only cookie).

**Request Body:** None — the refresh token is read from the `refresh_token` cookie.

**Response:**
```json
{
  "success": true,
  "message": "Token refreshed",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "username": "john.doe",
    "roles": ["EMPLOYEE"]
  }
}
```

**Note:** A new `refreshToken` is set as an HTTP-only cookie with the response.

**Errors:**
- `400`: Refresh token not found in cookie
- `401`: Invalid or revoked refresh token

---

#### POST /api/auth/logout

**Purpose**: Revoke the current access token (blacklisted) and all refresh tokens for the authenticated user. Clears the refresh token cookie.

**Authentication**: Required.

**Response:**
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

**Note:** The `refresh_token` cookie is cleared (max-age=0). The access token is blacklisted in Redis until its natural expiration.

---

#### GET /api/auth/me

**Purpose**: Get the current authenticated user's profile.

**Authentication**: Required.

**Response:**
```json
{
  "success": true,
  "message": null,
  "data": {
    "id": 1,
    "username": "john.doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "roles": ["EMPLOYEE"],
    "tenantId": 1,
    "departmentId": 1,
    "mfaEnabled": false
  }
}
```

---

#### POST /api/auth/change-password

**Purpose**: Change the current user's password.

**Authentication**: Required.

**Request Body:**
```json
{
  "currentPassword": "string (required)",
  "newPassword": "string (required)",
  "confirmPassword": "string (required)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Password changed successfully",
  "data": null
}
```

**Errors:**
- `400`: Passwords do not match, validation failed
- `401`: Current password is incorrect

---

#### POST /api/auth/forgot-password

**Purpose**: Request a password reset link via email.

**Authentication**: Not required.

**Request Body:**
```json
{
  "email": "string (required)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "If the email exists, a reset link has been sent",
  "data": null
}
```

**Errors:**
- `400`: Validation failed
- `429`: Rate limit exceeded

---

#### POST /api/auth/reset-password

**Purpose**: Reset password using a valid reset token.

**Authentication**: Not required.

**Request Body:**
```json
{
  "token": "string (required)",
  "newPassword": "string (required)",
  "confirmPassword": "string (required)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Password reset successfully",
  "data": null
}
```

**Errors:**
- `400`: Invalid/expired token, passwords do not match

---

### MFA Management

All MFA management endpoints require `MFA_MANAGE` authority (PLATFORM_ADMIN or TENANT_ADMIN only). MFA status is included in user management responses and `GET /api/auth/me`.

---

#### POST /api/mfa/enable

**Purpose**: Initiate MFA setup for the authenticated user.

**Authorization**: `MFA_MANAGE` authority required.

**Request Body:**
```json
{
  "method": "TOTP | EMAIL"
}
```

**Response (TOTP):**
```json
{
  "success": true,
  "message": "MFA setup initiated",
  "data": {
    "qrUri": "otpauth://totp/...",
    "secret": "JBSWY3DPEHPK3PXP",
    "method": "TOTP"
  }
}
```

**Response (EMAIL):**
```json
{
  "success": true,
  "message": "MFA setup initiated",
  "data": {
    "qrUri": null,
    "secret": null,
    "method": "EMAIL"
  }
}
```

---

#### POST /api/mfa/verify-setup

**Purpose**: Verify and activate MFA after initial setup.

**Authorization**: `MFA_MANAGE` authority required.

**Request Body:**
```json
{
  "code": "string (required)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "MFA enabled successfully",
  "data": null
}
```

**Errors:**
- `400`: MFA setup not initiated
- `401`: Invalid MFA code

---

#### POST /api/mfa/disable

**Purpose**: Disable MFA for the authenticated user.

**Authorization**: `MFA_MANAGE` authority required.

**Request Body:**
```json
{
  "password": "string (required)"
}
```

**Response:**
```json
{
  "success": true,
  "message": "MFA disabled successfully",
  "data": null
}
```

**Errors:**
- `401`: Current password is incorrect

---

### Expenses

---

#### GET /api/expenses

**Purpose**: List expenses. Scope depends on the user's role:
- Super admin: all expenses
- Auditor: all expenses in their tenant
- Finance: approved expenses in their tenant
- Manager: expenses in their department
- Employee: own expenses only

**Authentication**: Required.  
**Authority**: `EXPENSE_READ`

**Query Parameters:** `page`, `size`, `sort`

**Response:**
```json
{
  "success": true,
  "message": "Expenses retrieved",
  "data": {
    "content": [
      {
        "id": 1,
        "title": "Office Supplies",
        "description": "Pens and paper",
        "amount": 45.99,
        "category": "OFFICE",
        "status": "PENDING",
        "submissionDate": "2025-01-15T10:30:00Z",
        "decisionDate": null,
        "ownerUsername": "john.doe",
        "departmentName": "Engineering",
        "tenantName": "Acme Corp"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

#### GET /api/expenses/{id}

**Purpose**: Get a specific expense by ID.

**Authentication**: Required.  
**Authority**: `EXPENSE_READ`

**Path Parameters:** `id` (Long) — Expense ID

**Response:**
```json
{
  "success": true,
  "message": "Expense retrieved",
  "data": {
    "id": 1,
    "title": "Office Supplies",
    "description": "Pens and paper",
    "amount": 45.99,
    "category": "OFFICE",
    "status": "PENDING",
    "submissionDate": "2025-01-15T10:30:00Z",
    "decisionDate": null,
    "ownerUsername": "john.doe",
    "departmentName": "Engineering",
    "tenantName": "Acme Corp"
  }
}
```

**Errors:**
- `403`: Cannot view this expense
- `404`: Expense not found

---

#### POST /api/expenses

**Purpose**: Create a new expense.

**Authentication**: Required.  
**Authority**: `EXPENSE_CREATE`

**Request Body:**
```json
{
  "title": "Office Supplies",
  "description": "Pens and paper for Q1",
  "amount": 45.99,
  "category": "OFFICE",
  "departmentId": 1
}
```

**Response:**
```json
{
  "success": true,
  "message": "Expense created",
  "data": {
    "id": 1,
    "title": "Office Supplies",
    "description": "Pens and paper for Q1",
    "amount": 45.99,
    "category": "OFFICE",
    "status": "PENDING",
    "submissionDate": "2025-01-15T10:30:00Z",
    "decisionDate": null,
    "ownerUsername": "john.doe",
    "departmentName": "Engineering",
    "tenantName": "Acme Corp"
  }
}
```

**Errors:**
- `400`: Validation failed, user has no tenant
- `403`: Department must belong to the same tenant

---

#### PUT /api/expenses/{id}

**Purpose**: Update an existing expense (only PENDING expenses can be updated).

**Authentication**: Required.  
**Authority**: `EXPENSE_UPDATE`

**Path Parameters:** `id` (Long)

**Request Body:**
```json
{
  "title": "Updated Title",
  "description": "Updated description",
  "amount": 99.99,
  "category": "TRAVEL"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Expense updated",
  "data": { ... }
}
```

**Errors:**
- `400`: Only pending expenses can be updated
- `403`: Cannot edit this expense

---

#### POST /api/expenses/{id}/cancel

**Purpose**: Cancel an expense (only PENDING expenses can be cancelled).

**Authentication**: Required.  
**Authority**: `EXPENSE_UPDATE`

**Path Parameters:** `id` (Long)

**Response:**
```json
{
  "success": true,
  "message": "Expense cancelled",
  "data": { ... }
}
```

**Errors:**
- `400`: Only pending expenses can be cancelled
- `403`: Cannot cancel this expense

---

#### POST /api/expenses/{id}/approve

**Purpose**: Approve a pending expense.

**Authentication**: Required.  
**Authority**: `EXPENSE_APPROVE`

**Path Parameters:** `id` (Long)

**Response:**
```json
{
  "success": true,
  "message": "Expense approved",
  "data": { ... }
}
```

**Errors:**
- `400`: Only pending expenses can be approved
- `403`: Cannot approve this expense

---

#### POST /api/expenses/{id}/reject

**Purpose**: Reject a pending expense.

**Authentication**: Required.  
**Authority**: `EXPENSE_REJECT`

**Path Parameters:** `id` (Long)

**Response:**
```json
{
  "success": true,
  "message": "Expense rejected",
  "data": { ... }
}
```

**Errors:**
- `400`: Only pending expenses can be rejected
- `403`: Cannot reject this expense

---

#### POST /api/expenses/{id}/process

**Purpose**: Process an approved expense (finance action).

**Authentication**: Required.  
**Authority**: `EXPENSE_PROCESS`

**Path Parameters:** `id` (Long)

**Response:**
```json
{
  "success": true,
  "message": "Expense processed",
  "data": { ... }
}
```

**Errors:**
- `400`: Only approved expenses can be processed
- `403`: Cannot process this expense

---

### User Management

---

#### GET /api/management/users

**Purpose**: List users. Super admins see all users; tenant admins see users in their tenant.

**Authentication**: Required.  
**Authority**: `USER_READ`

**Query Parameters:** `page`, `size`, `sort`

**Response:**
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "username": "john.doe",
        "email": "john@example.com",
        "firstName": "John",
        "lastName": "Doe",
        "enabled": true,
        "mfaEnabled": false,
        "tenantId": 1,
        "departmentId": 1,
        "roles": ["EMPLOYEE"]
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

#### GET /api/management/users/{id}

**Purpose**: Get a specific user by ID.

**Authentication**: Required.  
**Authority**: `USER_READ`

**Response:**
```json
{
  "success": true,
  "message": "User retrieved successfully",
  "data": {
    "id": 1,
    "username": "john.doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "enabled": true,
    "mfaEnabled": false,
    "tenantId": 1,
    "departmentId": 1,
    "roles": ["EMPLOYEE"]
  }
}
```

---

#### POST /api/management/users

**Purpose**: Create a new user.

**Authentication**: Required.  
**Authority**: `USER_CREATE`

**Request Body:**
```json
{
  "username": "jane.doe",
  "email": "jane@example.com",
  "password": "SecurePass123!",
  "firstName": "Jane",
  "lastName": "Doe",
  "tenantId": 1,
  "departmentId": 1,
  "roleIds": [3]
}
```

**Response:**
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": 2,
    "username": "jane.doe",
    "email": "jane@example.com",
    "firstName": "Jane",
    "lastName": "Doe",
    "enabled": true,
    "mfaEnabled": false,
    "tenantId": 1,
    "departmentId": 1,
    "roles": ["EMPLOYEE"]
  }
}
```

**Errors:**
- `400`: Username/email already exists, validation failed

---

#### PUT /api/management/users/{id}

**Purpose**: Update an existing user.

**Authentication**: Required.  
**Authority**: `USER_WRITE`

**Request Body:**
```json
{
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane.smith@example.com",
  "departmentId": 2
}
```

**Response:**
```json
{
  "success": true,
  "message": "User updated successfully",
  "data": { ... }
}
```

---

#### DELETE /api/management/users/{id}

**Purpose**: Delete a user.

**Authentication**: Required.  
**Authority**: `USER_DELETE`

**Response:**
```json
{
  "success": true,
  "message": "User deleted successfully",
  "data": null
}
```

**Errors:**
- `400`: Cannot delete the last admin in a tenant

---

#### POST /api/management/users/{id}/enable

**Purpose**: Enable or disable a user account.

**Authentication**: Required.  
**Authority**: `USER_ENABLE`

**Request Body:**
```json
{
  "enabled": true
}
```

**Response:**
```json
{
  "success": true,
  "message": "User enabled state updated",
  "data": { ... }
}
```

**Errors:**
- `400`: Cannot disable the last admin in a tenant

---

#### POST /api/management/users/{id}/roles

**Purpose**: Assign a role to a user.

**Authentication**: Required.  
**Authority**: `USER_ASSIGN_ROLE`

**Request Body:**
```json
{
  "roleName": "DEPARTMENT_MANAGER"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Role assigned successfully",
  "data": { ... }
}
```

---

#### DELETE /api/management/users/{id}/roles

**Purpose**: Remove a role from a user.

**Authentication**: Required.  
**Authority**: `USER_ASSIGN_ROLE`

**Request Body:**
```json
{
  "roleName": "DEPARTMENT_MANAGER"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Role removed successfully",
  "data": { ... }
}
```

---

### Role Management

---

#### GET /api/management/roles

**Purpose**: List all roles.

**Authentication**: Required.  
**Authority**: `ROLE_READ`

**Response:**
```json
{
  "success": true,
  "message": "Roles retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "PLATFORM_ADMIN",
        "title": "Platform Administrator",
        "description": "Platform-wide administrator with unrestricted cross-tenant access",
        "permissions": ["USER_CREATE", "USER_UPDATE", "EXPENSE_READ", ...]
      }
    ],
    "totalElements": 7,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

#### GET /api/management/roles/{id}

**Purpose**: Get a specific role by ID.

**Authentication**: Required.  
**Authority**: `ROLE_READ`

---

#### POST /api/management/roles

**Purpose**: Create a new custom role.

**Authentication**: Required.  
**Authority**: `ROLE_WRITE`

**Request Body:**
```json
{
  "name": "CUSTOM_ROLE",
  "title": "Custom Role",
  "description": "A custom role"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Role created successfully",
  "data": {
    "id": 8,
    "name": "CUSTOM_ROLE",
    "title": "Custom Role",
    "description": "A custom role",
    "permissions": []
  }
}
```

**Errors:**
- `400`: Role already exists

---

#### PUT /api/management/roles/{id}

**Purpose**: Update a custom role (built-in roles cannot be updated).

**Authentication**: Required.  
**Authority**: `ROLE_WRITE`

**Errors:**
- `400`: Cannot update built-in role

---

#### DELETE /api/management/roles/{id}

**Purpose**: Delete a custom role (built-in roles cannot be deleted; roles assigned to users cannot be deleted).

**Authentication**: Required.  
**Authority**: `ROLE_DELETE`

**Errors:**
- `400`: Cannot delete built-in role, role is assigned to users

---

#### POST /api/management/roles/{id}/permissions

**Purpose**: Add a permission to a custom role.

**Authentication**: Required.  
**Authority**: `ROLE_ASSIGN_PERMISSION`

**Request Body:**
```json
{
  "permission": "EXPENSE_READ"
}
```

**Errors:**
- `400`: Cannot modify built-in role permissions

---

#### DELETE /api/management/roles/{id}/permissions

**Purpose**: Remove a permission from a custom role.

**Authentication**: Required.  
**Authority**: `ROLE_ASSIGN_PERMISSION`

**Errors:**
- `400`: Cannot modify built-in role permissions

---

### Tenant Management

---

#### GET /api/management/tenants

**Purpose**: List tenants. Super admins see all; tenant admins see only their tenant.

**Authentication**: Required.  
**Authority**: `TENANT_READ`

**Response:**
```json
{
  "success": true,
  "message": "Tenants retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Acme Corp",
        "status": "ACTIVE",
        "createdAt": "2025-01-01T00:00:00Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

#### GET /api/management/tenants/{id}

**Purpose**: Get a specific tenant by ID.

**Authentication**: Required.  
**Authority**: `TENANT_READ`

---

#### POST /api/management/tenants

**Purpose**: Create a new tenant.

**Authentication**: Required.  
**Authority**: `TENANT_CREATE`

**Request Body:**
```json
{
  "name": "New Corp",
  "status": "ACTIVE"
}
```

**Errors:**
- `400`: Tenant already exists

---

#### PUT /api/management/tenants/{id}

**Purpose**: Update a tenant.

**Authentication**: Required.  
**Authority**: `TENANT_UPDATE`

**Request Body:**
```json
{
  "name": "Updated Corp",
  "status": "ACTIVE"
}
```

---

#### DELETE /api/management/tenants/{id}

**Purpose**: Delete a tenant.

**Authentication**: Required.  
**Authority**: `TENANT_DELETE`

---

### Department Management

---

#### GET /api/management/departments

**Purpose**: List departments. Super admins see all; tenant admins see their tenant's departments.

**Authentication**: Required.  
**Authority**: `DEPARTMENT_READ`

**Response:**
```json
{
  "success": true,
  "message": "Departments retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Engineering",
        "tenantId": 1,
        "tenantName": "Acme Corp",
        "managerIds": [1, 2],
        "managerUsernames": ["john.doe", "jane.smith"]
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

#### GET /api/management/departments/{id}

**Purpose**: Get a specific department by ID.

**Authentication**: Required.  
**Authority**: `DEPARTMENT_READ`

---

#### POST /api/management/departments

**Purpose**: Create a new department.

**Authentication**: Required.  
**Authority**: `DEPARTMENT_CREATE`

**Request Body:**
```json
{
  "name": "Marketing",
  "tenantId": 1,
  "managerIds": [2, 3]
}
```

**Errors:**
- `400`: Department already exists in this tenant, manager must belong to the same tenant

---

#### PUT /api/management/departments/{id}

**Purpose**: Update a department.

**Authentication**: Required.  
**Authority**: `DEPARTMENT_UPDATE`

**Request Body:**
```json
{
  "name": "Marketing & Communications",
  "managerIds": [3, 4]
}
```

---

#### DELETE /api/management/departments/{id}

**Purpose**: Delete a department.

**Authentication**: Required.  
**Authority**: `DEPARTMENT_DELETE`

---

### Audit Logs

---

#### GET /api/management/audit

**Purpose**: List audit logs. Super admins see all; tenant admins see their tenant's logs.

**Authentication**: Required.  
**Authority**: `AUDIT_LOG_READ`

**Query Parameters:** `page`, `size`, `sort`

**Response:**
```json
{
  "success": true,
  "message": "Audit logs retrieved",
  "data": {
    "content": [
      {
        "id": 1,
        "actorId": 1,
        "actorUsername": "john.doe",
        "tenantId": 1,
        "action": "EXPENSE_CREATED",
        "resourceType": "EXPENSE",
        "resourceId": "1",
        "details": "Expense created",
        "timestamp": "2025-01-15T10:30:00Z"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

---

#### GET /api/management/audit/{id}

**Purpose**: Get a specific audit log by ID.

**Authentication**: Required.  
**Authority**: `AUDIT_LOG_READ`

---

## 6. API Groups

### Authentication
- `POST /api/auth/login` — User login
- `POST /api/auth/mfa/verify` — MFA verification
- `POST /api/auth/refresh` — Token refresh
- `POST /api/auth/logout` — User logout
- `GET /api/auth/me` — Current user profile
- `POST /api/auth/change-password` — Password change
- `POST /api/auth/forgot-password` — Password reset request
- `POST /api/auth/reset-password` — Password reset

### MFA Management (Admin-only)
- `POST /api/mfa/enable` — Initiate MFA setup
- `POST /api/mfa/verify-setup` — Verify MFA setup
- `POST /api/mfa/disable` — Disable MFA

### Expenses
- `GET /api/expenses` — List expenses
- `GET /api/expenses/{id}` — Get expense
- `POST /api/expenses` — Create expense
- `PUT /api/expenses/{id}` — Update expense
- `POST /api/expenses/{id}/cancel` — Cancel expense
- `POST /api/expenses/{id}/approve` — Approve expense
- `POST /api/expenses/{id}/reject` — Reject expense
- `POST /api/expenses/{id}/process` — Process expense

### User Management
- `GET /api/management/users` — List users
- `GET /api/management/users/{id}` — Get user
- `POST /api/management/users` — Create user
- `PUT /api/management/users/{id}` — Update user
- `DELETE /api/management/users/{id}` — Delete user
- `POST /api/management/users/{id}/enable` — Enable/disable user
- `POST /api/management/users/{id}/roles` — Assign role
- `DELETE /api/management/users/{id}/roles` — Remove role

### Role Management
- `GET /api/management/roles` — List roles
- `GET /api/management/roles/{id}` — Get role
- `POST /api/management/roles` — Create role
- `PUT /api/management/roles/{id}` — Update role
- `DELETE /api/management/roles/{id}` — Delete role
- `POST /api/management/roles/{id}/permissions` — Add permission
- `DELETE /api/management/roles/{id}/permissions` — Remove permission

### Tenant Management
- `GET /api/management/tenants` — List tenants
- `GET /api/management/tenants/{id}` — Get tenant
- `POST /api/management/tenants` — Create tenant
- `PUT /api/management/tenants/{id}` — Update tenant
- `DELETE /api/management/tenants/{id}` — Delete tenant

### Department Management
- `GET /api/management/departments` — List departments
- `GET /api/management/departments/{id}` — Get department
- `POST /api/management/departments` — Create department
- `PUT /api/management/departments/{id}` — Update department
- `DELETE /api/management/departments/{id}` — Delete department

### Audit Logs
- `GET /api/management/audit` — List audit logs
- `GET /api/management/audit/{id}` — Get audit log

## 7. Error Handling

### Standard Error Response

```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "errors": {
    "field": "Field-level error message"
  }
}
```

### Error Examples

**Validation Error (400):**
```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "errors": {
    "title": "must not be blank",
    "amount": "must be greater than 0"
  }
}
```

**Authentication Error (401):**
```json
{
  "success": false,
  "message": "Invalid credentials",
  "data": null,
  "errors": null
}
```

**Authorization Error (403):**
```json
{
  "success": false,
  "message": "Access denied: Insufficient permissions",
  "data": null,
  "errors": null
}
```

**Resource Not Found (400):**
```json
{
  "success": false,
  "message": "Expense not found",
  "data": null,
  "errors": null
}
```

**Business Rule Violation (400):**
```json
{
  "success": false,
  "message": "Only pending expenses can be updated",
  "data": null,
  "errors": null
}
```

**Account Locked (429):**
```json
{
  "success": false,
  "message": "Account is locked due to too many failed attempts. Try again later.",
  "data": null,
  "errors": null
}
```

**Unexpected Error (500):**
```json
{
  "success": false,
  "message": "An unexpected error occurred",
  "data": null,
  "errors": null
}
```

## 8. Pagination and Filtering

### Pagination Parameters
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | Integer | 0 | Page number (0-indexed) |
| `size` | Integer | 20 | Page size |
| `sort` | String | — | Sort criteria (e.g., `createdAt,desc`) |

### Sorting
Use the `sort` query parameter with field name and direction:
```
GET /api/expenses?sort=amount,desc
GET /api/expenses?sort=createdAt,asc&sort=title,desc
```

### Filtering
- No general-purpose filtering is implemented.
- List endpoints automatically scope results based on the user's role and tenant.

## 9. Idempotency and Concurrency

- No explicit idempotency keys.
- Refresh token rotation provides implicit idempotency for token refresh.
- Optimistic locking is not implemented; concurrent updates to the same resource may cause conflicts.

## 10. Rate Limiting

### Configuration
| Endpoint | Limit | Window |
|----------|-------|--------|
| `POST /api/auth/forgot-password` | 10 requests per identifier | 1 minute |
| `POST /api/auth/reset-password` | 10 requests per identifier | 1 minute |
| `POST /api/auth/mfa/verify` | 10 requests per username | 1 minute |

### Implementation
- Redis-based sliding window rate limiting via Lua script.
- Identifier: client IP (for anonymous endpoints) or username (for authenticated endpoints).
- Trusted proxy support for accurate `X-Forwarded-For` IP resolution.

### Rate Limit Response
- `429 Too Many Requests` with message "Too many requests. Please try again later."

## 11. API Security Considerations

### Sensitive Endpoints
| Endpoint | Sensitivity | Notes |
|----------|-------------|-------|
| `POST /api/auth/login` | High | Credential verification, rate limited |
| `POST /api/auth/forgot-password` | High | Email enumeration risk, rate limited |
| `POST /api/management/users` | High | User creation, requires USER_CREATE |
| `DELETE /api/management/users/{id}` | High | User deletion, requires USER_DELETE |
| `POST /api/management/roles/{id}/permissions` | High | Permission escalation risk |

### Input Validation
- All write endpoints validate request bodies via Jakarta Bean Validation.
- Passwords are validated via custom `@Password` annotation.

### Sensitive Response Fields
- Passwords and MFA secrets are never included in API responses.
- Refresh tokens are only stored in HTTP-only cookies, never returned in response bodies.

## 12. Integration Examples

### Login with MFA

```bash
# Step 1: Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"john.doe","password":"SecurePass123!"}'

# Response: {"mfaRequired":true,"mfaSessionToken":"abc123...","method":"TOTP"}

# Step 2: Verify MFA
curl -X POST http://localhost:8080/api/auth/mfa/verify \
  -H "Content-Type: application/json" \
  -d '{"mfaSessionToken":"abc123...","code":"123456"}'

# Response: {"accessToken":"eyJ...","refreshToken":"eyJ...","expiresIn":900}
```

### Create Expense

```bash
curl -X POST http://localhost:8080/api/expenses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJ..." \
  -d '{
    "title": "Business Travel",
    "description": "Client meeting in NYC",
    "amount": 1250.00,
    "category": "TRAVEL",
    "departmentId": 1
  }'
```

### Paginated User List

```bash
curl -X GET "http://localhost:8080/api/management/users?page=0&size=10&sort=username,asc" \
  -H "Authorization: Bearer eyJ..."
```

## 13. API Change Notes

- No versioning is implemented. Breaking changes will be made in-place.
- No deprecation notices exist.

## 14. Undocumented or Ambiguous APIs

- **`GET /api/public/**`**: Permitted in `SecurityConfig` but no public controllers implement this path pattern. This may be a placeholder for future use.
- **`/error`**: Spring Boot's default error endpoint is permitted for unauthenticated access.
- **`/actuator/**`**: Health endpoint is public; all other actuator endpoints require authentication.
