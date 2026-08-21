# Frontend Integration Guide

## System Overview

This is a **multi-tenant expense management backend** built with Spring Boot 3.2.5. It provides a REST API for authentication, user/role/tenant/department management, expense submission/approval workflows, and audit logging.

**Base URL**: `https://api.example.com` (configurable via `app.base-url`)

**Frontend Origin**: Must be configured via `app.frontend-url` or `FRONTEND_URL` env var (default: `http://localhost:3000` in development)

> **Production Requirement**: All production deployments MUST use HTTPS. The backend expects TLS termination at the reverse proxy/load balancer level. HTTP-only refresh token cookies require `Secure` flag, which only works over HTTPS.

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Backend | Java 21, Spring Boot 3.2.5 |
| Database | H2 (dev/test), PostgreSQL (prod) |
| Cache/State | Redis (tokens, rate limiting, MFA sessions) |
| Auth | JWT (HS512) + HTTP-only refresh token cookie |

## Security Architecture

### Token Flow

```
┌─────────────┐    1. Login (credentials)    ┌─────────────┐
│   Frontend  │ ────────────────────────────▶ │   Backend   │
│  (SPA/Mobile)│                              │  (API)      │
│             │ ◀──────────────────────────── │             │
│             │    2. Access Token (body)     │             │
│             │    3. Refresh Token (cookie)  │             │
│             │                              │             │
│             │    4. API Request + Bearer    │             │
│             │ ────────────────────────────▶ │             │
│             │                              │             │
│             │    5. 401 Unauthorized        │             │
│             │ ◀──────────────────────────── │             │
│             │                              │             │
│             │    6. Refresh (cookie auto)   │             │
│             │ ────────────────────────────▶ │             │
│             │ ◀──────────────────────────── │             │
│             │    7. New Access Token        │             │
│             │    8. Retry original request  │             │
│             │ ────────────────────────────▶ │             │
└─────────────┘                              └─────────────┘
```

### Token Security Properties

| Token | Storage | Security | Expiration |
|-------|---------|----------|------------|
| Access Token | In-memory only (JS variable) | HS512 signed, blacklistable via Redis JTI | 15 minutes |
| Refresh Token | HTTP-only cookie | Cannot be accessed via JavaScript; Secure, SameSite=Strict | 7 days |
| MFA Session | HTTP-only cookie | Temporary, single-use | 5 minutes |

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

> **Important**: The refresh token is ONLY set as an HTTP-only cookie. It is NEVER returned in the response body. The frontend MUST NOT attempt to read, store, or manipulate the refresh token directly.

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

> **Critical**: The backend implements refresh token rotation. Each refresh issues a new refresh token and revokes the old one. The frontend MUST handle concurrent 401 responses correctly to avoid race conditions.

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
// Axios example with security best practices
import axios from 'axios';

// Token stored in memory (NOT localStorage/sessionStorage)
let accessToken = null;

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  withCredentials: true, // Required for refresh token cookie
  headers: {
    'Content-Type': 'application/json',
  },
  // Timeout to prevent hanging requests
  timeout: 10000,
});

// Request interceptor - attach access token
api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// Response interceptor - handle 401 (token refresh) with race condition protection
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(token);
    }
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      // If already refreshing, queue this request
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const { data } = await api.post('/api/auth/refresh');
        accessToken = data.data.accessToken;
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        processQueue(null, accessToken);
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError);
        accessToken = null;
        // Redirect to login - use your router's navigate function
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);

export { accessToken };
export default api;
```

> **Security Note**: Access tokens are stored in a JavaScript variable (in-memory), NOT in `localStorage` or `sessionStorage`. This prevents XSS attacks from stealing tokens. Tokens are lost on page reload, requiring re-authentication — this is by design for security.

### 2. Token Storage

| Token | Storage | Notes |
|-------|---------|-------|
| Access Token | In-memory JavaScript variable | 15-minute expiration; lost on page refresh (by design) |
| Refresh Token | HTTP-only cookie (`refresh_token`) | Automatically sent with requests; NOT accessible via JavaScript |

> **OWASP Recommendation**: NEVER store access tokens in `localStorage` or `sessionStorage`. These are accessible to any JavaScript running on the page, making them vulnerable to XSS attacks. Use in-memory storage for SPAs.

> **Alternative**: If persistent token storage is required (e.g., for offline capability), use the Backend-for-Frontend (BFF) pattern where the backend manages tokens and issues session cookies to the frontend.

### 3. Authentication State

```javascript
// React example with in-memory token
import { useState, useEffect, createContext, useContext } from 'react';
import api, { accessToken } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Check auth on app load via cookie-based refresh token
    // The access token is NOT persisted, so we attempt a refresh on load
    const checkAuth = async () => {
      try {
        const { data } = await api.post('/api/auth/refresh');
        accessToken = data.data.accessToken;
        const profileRes = await api.get('/api/auth/me');
        setUser(profileRes.data.data);
        setIsAuthenticated(true);
      } catch {
        // Not authenticated - user needs to log in
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    };
    checkAuth();
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, setUser, setIsAuthenticated }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
```

> **Security Note**: On page load, the access token is NOT in memory. The app attempts a refresh using the HTTP-only cookie. If the refresh fails (expired/invalid cookie), the user is directed to login. This is more secure than persisting tokens.

### 4. Login Flow

```javascript
import { useAuth } from './AuthProvider';

export function LoginPage() {
  const { setUser, setIsAuthenticated } = useAuth();

  const login = async (usernameOrEmail, password) => {
    try {
      const { data } = await api.post('/api/auth/login', {
        usernameOrEmail,
        password,
      });

      if (data.data.mfaRequired) {
        return { mfaRequired: true, mfaSessionToken: data.data.mfaSessionToken };
      }

      // Store access token in memory only (NOT localStorage)
      accessToken = data.data.accessToken;
      setUser(data.data);
      setIsAuthenticated(true);
      return { success: true };
    } catch (error) {
      // Handle specific error codes
      if (error.response?.status === 429) {
        return { error: 'Account locked. Please try again later.' };
      }
      if (error.response?.status === 401) {
        return { error: 'Invalid credentials.' };
      }
      return { error: 'Login failed. Please try again.' };
    }
  };

  const verifyMfa = async (mfaSessionToken, code) => {
    const { data } = await api.post('/api/auth/mfa/verify', {
      mfaSessionToken,
      code,
    });

    accessToken = data.data.accessToken;
    setUser(data.data);
    setIsAuthenticated(true);
  };

  return { login, verifyMfa };
}
```

> **Security Note**: Never log or store credentials. The login function only stores the access token in memory. The refresh token is automatically set as an HTTP-only cookie by the backend.

### 5. Logout

```javascript
const logout = async () => {
  try {
    await api.post('/api/auth/logout');
  } catch (error) {
    // Even if the request fails, clean up client state
    console.error('Logout request failed:', error);
  } finally {
    // Always clear client state regardless of server response
    accessToken = null;
    setUser(null);
    setIsAuthenticated(false);
    // Redirect to login
    window.location.href = '/login';
  }
};
```

> **Security Note**: Always clear ALL client-side state on logout, including:
> - In-memory tokens
> - React state (via context reset)
> - Any cached data (clear query cache if using React Query/TanStack Query)
> - WebSocket connections (if any)
> - Browser history manipulation (use `replaceState` to prevent back-button access)

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
| POST | `/api/management/users/{id}/mfa/enable` | Yes | Enable MFA |
| POST | `/api/management/users/{id}/mfa/disable` | Yes | Disable MFA |
| POST | `/api/management/users/{id}/mfa/reset` | Yes | Reset MFA |

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

### Token Security

1. **Access Token Storage**: Access tokens MUST be stored in-memory only (JavaScript variable). NEVER use `localStorage`, `sessionStorage`, or any other persistent storage. This prevents XSS attacks from stealing tokens.

2. **Refresh Token Cookie**: HTTP-only, Secure, SameSite=Strict. Cannot be accessed via JavaScript. Automatically sent with requests to the same origin.

3. **Token Rotation**: Refresh tokens are rotated on every use. The old token is revoked when a new one is issued.

4. **Concurrent Request Handling**: Use a request queue to prevent multiple simultaneous refresh attempts (see HTTP Client Setup example).

### Transport Security

5. **HTTPS Required**: Production deployments MUST use HTTPS. The refresh token cookie has the `Secure` flag, which requires HTTPS.

6. **HSTS**: The backend sets `Strict-Transport-Security` header. Frontend should also set this header via meta tag or web server configuration.

### CORS and CSRF

7. **CORS**: The backend allows a single origin. Ensure `app.frontend-url` matches your frontend URL exactly (including protocol and port).

8. **CSRF Protection**: The backend has CSRF disabled (stateless JWT API). For cookie-based refresh tokens, the `SameSite=Strict` flag provides CSRF protection. For additional protection, validate `Origin` and `Referer` headers on state-changing requests.

### XSS Prevention

9. **Content Security Policy**: The backend sets CSP headers. Frontend should also implement CSP:
```html
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';">
```

10. **Output Encoding**: Always encode user-provided data before rendering. Use framework-provided escaping (React automatically escapes JSX).

11. **Input Sanitization**: Sanitize user input on both client and server side. Never use `innerHTML` or `dangerouslySetInnerHTML` with untrusted data.

### Rate Limiting

12. **Rate Limit Handling**: Sensitive endpoints (login, forgot-password, MFA verify) are rate-limited. Implement exponential backoff:
```javascript
// Exponential backoff for rate-limited requests
const retryWithBackoff = async (fn, maxRetries = 3) => {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await fn();
    } catch (error) {
      if (error.response?.status === 429 && i < maxRetries - 1) {
        const delay = Math.pow(2, i) * 1000; // 1s, 2s, 4s
        await new Promise(resolve => setTimeout(resolve, delay));
      } else {
        throw error;
      }
    }
  }
};
```

13. **Account Lockout**: After 5 failed login attempts, the account is locked for 15 minutes. Display appropriate error messages to users.

### Token Expiration

14. **Proactive Refresh**: Consider refreshing tokens before they expire to avoid 401 errors during active use:
```javascript
// Refresh token 5 minutes before expiration
const scheduleTokenRefresh = (expiresIn) => {
  const refreshTime = (expiresIn - 300) * 1000; // 5 minutes before expiry
  setTimeout(() => {
    api.post('/api/auth/refresh').catch(() => {
      // Refresh failed, user needs to re-authenticate
    });
  }, refreshTime);
};
```

15. **401 Handling**: The frontend should handle 401 responses by attempting a token refresh. If refresh fails, redirect to login.

## Development Setup

1. Ensure Redis is running on `localhost:6379`
2. Set `app.frontend-url` to your frontend dev server URL (e.g., `http://localhost:3000`)
3. Set `JWT_SECRET` environment variable (minimum 32 characters, 64+ recommended for HS512)
4. Run the backend: `./mvnw spring-boot:run`
5. Configure your frontend to use `http://localhost:8080` as the API base URL
6. Ensure your frontend sends `withCredentials: true` for all API requests
7. **DO NOT** store tokens in `localStorage` or `sessionStorage` - use in-memory storage only
8. Implement proper error handling for 401, 403, 429 responses

> **Security Reminder**: Development uses HTTP for convenience. Production MUST use HTTPS. Never test security-sensitive features without HTTPS.

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
// Race condition protection: queues concurrent requests during refresh
```

### Protected Routes

```javascript
// React Router example
const ProtectedRoute = ({ children, requiredPermission }) => {
  const { isAuthenticated, isLoading, user } = useAuth();

  if (isLoading) {
    return <LoadingSpinner />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // Check specific permission if required
  if (requiredPermission && !user?.permissions?.includes(requiredPermission)) {
    return <Navigate to="/unauthorized" replace />;
  }

  return children;
};
```

### Role-Based UI

```javascript
// Check user roles for UI elements
const useAuthorization = () => {
  const { user } = useAuth();

  const canApprove = user?.roles?.includes('DEPARTMENT_MANAGER') || 
                     user?.roles?.includes('TENANT_ADMIN') ||
                     user?.roles?.includes('PLATFORM_ADMIN');

  const canManageUsers = user?.roles?.includes('USER_MANAGER') ||
                         user?.roles?.includes('TENANT_ADMIN') ||
                         user?.roles?.includes('PLATFORM_ADMIN');

  const canViewExpenses = user?.permissions?.includes('EXPENSE_READ');

  return { canApprove, canManageUsers, canViewExpenses };
};

// Usage in component
const ExpenseActions = ({ expense }) => {
  const { canApprove } = useAuthorization();

  return (
    <div>
      {canApprove && (
        <button onClick={() => approveExpense(expense.id)}>
          Approve
        </button>
      )}
    </div>
  );
};
```

### Secure Error Handling

```javascript
// Global error handler
const handleApiError = (error) => {
  const status = error.response?.status;
  const message = error.response?.data?.message;

  switch (status) {
    case 400:
      // Validation error - show specific field errors
      return { type: 'validation', errors: error.response.data.errors };
    case 401:
      // Unauthorized - refresh token might be expired
      return { type: 'auth', message: 'Please log in again' };
    case 403:
      // Forbidden - user doesn't have permission
      return { type: 'permission', message: 'You do not have permission' };
    case 404:
      // Not found
      return { type: 'notFound', message: 'Resource not found' };
    case 409:
      // Conflict
      return { type: 'conflict', message: message || 'Conflict occurred' };
    case 429:
      // Rate limited
      return { type: 'rateLimit', message: 'Too many requests. Please wait.' };
    case 500:
      // Server error - don't expose internal details
      return { type: 'server', message: 'An unexpected error occurred' };
    default:
      return { type: 'unknown', message: 'An error occurred' };
  }
};
```

### Secure API Communication

```javascript
// Always validate responses before processing
const validateResponse = (response) => {
  if (!response?.data?.success) {
    throw new Error(response?.data?.message || 'Request failed');
  }
  return response.data.data;
};

// Never log sensitive data
const apiCall = async () => {
  try {
    const { data } = await api.get('/api/expenses');
    return validateResponse(data);
  } catch (error) {
    // Log error without sensitive data
    console.error('API Error:', error.message);
    throw error;
  }
};
```

### Session Management

```javascript
// Handle browser focus/visibility changes
const useSessionManagement = () => {
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        // Tab became visible - verify token is still valid
        api.get('/api/auth/me').catch(() => {
          // Token expired while tab was hidden
          // Refresh will be attempted automatically by interceptor
        });
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);
};
```

### Cross-Origin Request Protection

```javascript
// For SPAs deployed on different domains than the API
// Ensure proper CORS configuration on backend

// Frontend should validate origin in production
const validateOrigin = () => {
  const allowedOrigins = [
    'https://app.example.com',
    'https://admin.example.com',
  ];

  if (!allowedOrigins.includes(window.location.origin)) {
    console.error('Untrusted origin:', window.location.origin);
    // Optionally redirect to a safe page
  }
};

// Run on app initialization
validateOrigin();
```
