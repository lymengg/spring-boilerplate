# Frontend Integration Guide

## System Overview

This is a **multi-tenant expense management backend** built with Spring Boot 3.2.5. It provides a REST API for authentication, user/role/tenant/department management, expense submission/approval workflows, and audit logging.

**Base URL**: `http://localhost:8080` (configurable via `app.base-url`)

**Frontend Origin**: Must be configured via `app.frontend-url` or `FRONTEND_URL` env var (default: `http://localhost:3000`)

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Backend | Java 21, Spring Boot 3.2.5 |
| Database | H2 (dev/test), PostgreSQL (prod) |
| Cache/State | Redis (tokens, rate limiting, MFA sessions) |
| Auth | JWT (HS512) + HTTP-only refresh token cookie |

## Authentication Flow

### Login (No MFA)

```
POST /api/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "string",
  "password": "string"
}
```

**Response:**
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

**Cookie Set:** `refresh_token` (HTTP-only, Secure, SameSite=Strict, 7 days)

### Login (MFA Required)

If MFA is enabled, the response is:
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

Then call:
```
POST /api/auth/mfa/verify
Content-Type: application/json

{
  "mfaSessionToken": "abc123...",
  "code": "123456"
}
```

### Token Refresh

The refresh token is automatically sent via HTTP-only cookie. When the access token expires (401 response), call:

```
POST /api/auth/refresh
```

No request body needed — the refresh token is read from the `refresh_token` cookie.

**Response:** Same as login response (new access token + new refresh token cookie).

### Logout

```
POST /api/auth/logout
Authorization: Bearer <accessToken>
```

Clears the refresh token cookie and blacklists the access token.

## API Response Format

All responses use the `ApiResponse` envelope:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Error response:**
```json
{
  "success": false,
  "message": "Error description",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Validation error:**
```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

## Error Codes

| HTTP Status | Meaning | Action |
|-------------|---------|--------|
| 200 | Success | Process response |
| 400 | Validation error / bad request | Check `message` for details |
| 401 | Invalid/missing token or credentials | Re-authenticate |
| 403 | Insufficient permissions | Check user roles |
| 404 | Resource not found | Check resource ID |
| 409 | Conflict (e.g., duplicate name) | Check `message` |
| 429 | Rate limit / account locked | Wait and retry |
| 500 | Server error | Contact backend team |

## Frontend Integration Checklist

### 1. HTTP Client Setup

```javascript
// Axios example
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true, // Required for refresh token cookie
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - attach access token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor - handle 401 (token refresh)
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401 && !error.config._retry) {
      error.config._retry = true;
      try {
        const { data } = await api.post('/api/auth/refresh');
        localStorage.setItem('accessToken', data.data.accessToken);
        error.config.headers.Authorization = `Bearer ${data.data.accessToken}`;
        return api(error.config);
      } catch (refreshError) {
        localStorage.removeItem('accessToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);
```

### 2. Token Storage

| Token | Storage | Notes |
|-------|---------|-------|
| Access Token | `localStorage` or in-memory variable | 15-minute expiration |
| Refresh Token | HTTP-only cookie (`refresh_token`) | Automatically sent with requests; not accessible via JavaScript |

### 3. Authentication State

```javascript
// React example
const [user, setUser] = useState(null);
const [isAuthenticated, setIsAuthenticated] = useState(false);

// Check auth on app load
useEffect(() => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    api.get('/api/auth/me')
      .then(res => {
        setUser(res.data.data);
        setIsAuthenticated(true);
      })
      .catch(() => {
        localStorage.removeItem('accessToken');
      });
  }
}, []);
```

### 4. Login Flow

```javascript
const login = async (usernameOrEmail, password) => {
  const { data } = await api.post('/api/auth/login', {
    usernameOrEmail,
    password,
  });

  if (data.data.mfaRequired) {
    // Show MFA input form
    return { mfaRequired: true, mfaSessionToken: data.data.mfaSessionToken };
  }

  // Store access token (refresh token is in cookie)
  localStorage.setItem('accessToken', data.data.accessToken);
  setUser(data.data);
  setIsAuthenticated(true);
  return { success: true };
};

const verifyMfa = async (mfaSessionToken, code) => {
  const { data } = await api.post('/api/auth/mfa/verify', {
    mfaSessionToken,
    code,
  });

  localStorage.setItem('accessToken', data.data.accessToken);
  setUser(data.data);
  setIsAuthenticated(true);
};
```

### 5. Logout

```javascript
const logout = async () => {
  await api.post('/api/auth/logout');
  localStorage.removeItem('accessToken');
  setUser(null);
  setIsAuthenticated(false);
};
```

### 6. Password Change

```javascript
const changePassword = async (currentPassword, newPassword, confirmPassword) => {
  await api.post('/api/auth/change-password', {
    currentPassword,
    newPassword,
    confirmPassword,
  });
  // Refresh token cookie is cleared on password change
  // User needs to re-authenticate
};
```

## API Endpoints Reference

### Authentication

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/login` | No | Login |
| POST | `/api/auth/mfa/verify` | No | MFA verification |
| POST | `/api/auth/refresh` | No (cookie) | Token refresh |
| POST | `/api/auth/logout` | Yes | Logout |
| GET | `/api/auth/me` | Yes | Current user profile |
| POST | `/api/auth/change-password` | Yes | Change password |
| POST | `/api/auth/forgot-password` | No | Request password reset |
| POST | `/api/auth/reset-password` | No | Reset password |

### MFA Management (Admin)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/mfa/enable` | Yes | Initiate MFA setup |
| POST | `/api/mfa/verify-setup` | Yes | Verify MFA setup |
| POST | `/api/mfa/disable` | Yes | Disable MFA |

### Expenses

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/expenses` | Yes | List expenses (role-scoped) |
| GET | `/api/expenses/{id}` | Yes | Get expense |
| POST | `/api/expenses` | Yes | Create expense |
| PUT | `/api/expenses/{id}` | Yes | Update expense |
| POST | `/api/expenses/{id}/cancel` | Yes | Cancel expense |
| POST | `/api/expenses/{id}/approve` | Yes | Approve expense |
| POST | `/api/expenses/{id}/reject` | Yes | Reject expense |
| POST | `/api/expenses/{id}/process` | Yes | Process expense |

### User Management

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/management/users` | Yes | List users |
| GET | `/api/management/users/{id}` | Yes | Get user |
| POST | `/api/management/users` | Yes | Create user |
| PUT | `/api/management/users/{id}` | Yes | Update user |
| DELETE | `/api/management/users/{id}` | Yes | Delete user |
| POST | `/api/management/users/{id}/enable` | Yes | Enable/disable user |
| POST | `/api/management/users/{id}/roles` | Yes | Assign role |
| DELETE | `/api/management/users/{id}/roles` | Yes | Remove role |

### Role Management

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/management/roles` | Yes | List roles |
| GET | `/api/management/roles/{id}` | Yes | Get role |
| POST | `/api/management/roles` | Yes | Create role |
| PUT | `/api/management/roles/{id}` | Yes | Update role |
| DELETE | `/api/management/roles/{id}` | Yes | Delete role |
| POST | `/api/management/roles/{id}/permissions` | Yes | Add permission |
| DELETE | `/api/management/roles/{id}/permissions` | Yes | Remove permission |

### Tenant Management

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/management/tenants` | Yes | List tenants |
| GET | `/api/management/tenants/{id}` | Yes | Get tenant |
| POST | `/api/management/tenants` | Yes | Create tenant |
| PUT | `/api/management/tenants/{id}` | Yes | Update tenant |
| DELETE | `/api/management/tenants/{id}` | Yes | Delete tenant |

### Department Management

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/management/departments` | Yes | List departments |
| GET | `/api/management/departments/{id}` | Yes | Get department |
| POST | `/api/management/departments` | Yes | Create department |
| PUT | `/api/management/departments/{id}` | Yes | Update department |
| DELETE | `/api/management/departments/{id}` | Yes | Delete department |

### Audit Logs

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/management/audit` | Yes | List audit logs |
| GET | `/api/management/audit/{id}` | Yes | Get audit log |

## Pagination

All list endpoints support pagination:

```
GET /api/expenses?page=0&size=20&sort=createdAt,desc
```

**Response:**
```json
{
  "content": [ ... ],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

## Roles and Permissions

### Built-in Roles

| Role | Description |
|------|-------------|
| PLATFORM_ADMIN | Full system admin (all tenants) |
| TENANT_ADMIN | Tenant-scoped admin |
| USER_MANAGER | User management within tenant |
| DEPARTMENT_MANAGER | Expense approval |
| EMPLOYEE | Regular user |
| AUDITOR | Read-only compliance |
| FINANCE | Payment processing |

### Permissions

29 granular permissions: `USER_CREATE`, `USER_READ`, `USER_UPDATE`, `USER_DELETE`, `USER_ENABLE`, `USER_ASSIGN_ROLE`, `ROLE_READ`, `ROLE_WRITE`, `ROLE_DELETE`, `ROLE_ASSIGN_PERMISSION`, `TENANT_READ`, `TENANT_CREATE`, `TENANT_UPDATE`, `TENANT_DELETE`, `DEPARTMENT_READ`, `DEPARTMENT_CREATE`, `DEPARTMENT_UPDATE`, `DEPARTMENT_DELETE`, `EXPENSE_READ`, `EXPENSE_CREATE`, `EXPENSE_UPDATE`, `EXPENSE_DELETE`, `EXPENSE_APPROVE`, `EXPENSE_REJECT`, `EXPENSE_PROCESS`, `MFA_MANAGE`, `REPORT_READ`, `AUDIT_LOG_READ`

## Expense Status Lifecycle

```
PENDING → APPROVED → PROCESSED
PENDING → REJECTED
PENDING → CANCELLED
```

## Security Notes

1. **Refresh Token Cookie**: HTTP-only, Secure, SameSite=Strict. Cannot be accessed via JavaScript. Automatically sent with requests to the same origin.

2. **CORS**: The backend allows a single origin. Ensure `app.frontend-url` matches your frontend dev server URL.

3. **Rate Limiting**: Sensitive endpoints (forgot-password, reset-password, MFA verify) are rate-limited to 10 requests per minute per identifier.

4. **Account Lockout**: After 5 failed login attempts, the account is locked for 15 minutes.

5. **Token Expiration**: Access tokens expire in 15 minutes. The frontend should handle 401 responses by attempting a token refresh via the cookie.

## Development Setup

1. Ensure Redis is running on `localhost:6379`
2. Set `app.frontend-url` to your frontend dev server URL
3. Set `JWT_SECRET` environment variable (minimum 32 characters)
4. Run the backend: `./mvnw spring-boot:run`
5. Configure your frontend to use `http://localhost:8080` as the API base URL
6. Ensure your frontend sends `withCredentials: true` for all API requests

## CORS Configuration

The backend uses these CORS settings:
- **Allowed Origin**: `app.frontend-url` (default: `http://localhost:3000`)
- **Allowed Methods**: GET, POST, PUT, PATCH, DELETE, OPTIONS
- **Allowed Headers**: Authorization, Content-Type, X-Requested-With, Accept, Origin
- **Credentials**: true (required for cookie-based refresh token)
- **Max Age**: 3600 seconds

## Common Frontend Patterns

### Handling Token Expiration

```javascript
// Axios interceptor handles 401 responses automatically
// On 401: tries to refresh token via cookie
// On refresh failure: clears token and redirects to login
```

### Protected Routes

```javascript
// React Router example
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? children : <Navigate to="/login" />;
};
```

### Role-Based UI

```javascript
// Check user roles for UI elements
const canApprove = user?.roles?.includes('DEPARTMENT_MANAGER') || 
                   user?.roles?.includes('TENANT_ADMIN') ||
                   user?.roles?.includes('PLATFORM_ADMIN');
```
