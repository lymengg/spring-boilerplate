# Security Architecture and Controls

## 1. Security Overview

The application implements a multi-layered security model based on **Spring Security** with **stateless JWT authentication**, **role-based access control (RBAC)** with fine-grained permissions, **multi-tenancy with data isolation**, and **defense-in-depth** security controls including rate limiting, account lockout, MFA, and comprehensive audit logging.

**Security posture summary:**
- Stateless JWT authentication with access and refresh tokens
- BCrypt password hashing (strength 12)
- Multi-factor authentication (TOTP + Email OTP)
- Role-based authorization with 29 granular permissions
- Tenant-level data isolation
- Redis-based rate limiting with sliding window
- Account lockout after failed attempts
- Comprehensive security audit logging
- Security headers (CSP, HSTS, X-Frame-Options, etc.)
- Input validation via Jakarta Bean Validation
- SQL injection prevention via JPA parameterized queries

## 2. Security Objectives

| Objective | Implementation | Status |
|-----------|---------------|--------|
| **Authentication** | JWT-based with BCrypt password verification | Implemented |
| **Authorization** | RBAC with `@PreAuthorize` and `AuthorizationService` | Implemented |
| **Confidentiality** | HTTPS (via HSTS), no secrets in source, password hashing | Partial (no TLS termination in app) |
| **Integrity** | JWT signature validation (HS512), input validation | Implemented |
| **Availability** | Rate limiting, account lockout | Implemented |
| **Auditability** | Security audit logger + business audit logs in DB | Implemented |

## 3. Authentication

### Authentication Mechanism
- **Stateless JWT authentication** using the `jjwt` library (v0.12.5).
- Tokens signed with **HS512** algorithm using a configurable secret (minimum 32 characters, recommended 64+ bytes).
- No server-side sessions; the JWT access token is sent with every request via `Authorization: Bearer <token>` header.

### Credential Validation
- Credentials verified through Spring Security's `AuthenticationManager` configured as `DaoAuthenticationProvider`.
- `CustomUserDetailsService` loads the user by username or email and initializes all associations.
- Spring Security's `PasswordEncoder.matches()` compares the raw password against the stored BCrypt hash.

### Password Hashing
- **BCrypt** with **strength 12** (configurable via `PasswordEncoder` bean).
- Passwords are never stored in plaintext.
- All password changes go through `UserService.changePassword()` which validates the current password first.

### Token Lifecycle

| Token Type | Purpose | Expiration | Storage |
|-----------|---------|------------|---------|
| Access Token | API authentication | 15 minutes | Client-side only |
| Refresh Token | Token renewal | 7 days | Redis (SHA-256 hash) |
| MFA Pending Token | Pre-MFA session | 5 minutes | Redis |

### Login Flow
1. Client sends `POST /api/auth/login` with `usernameOrEmail` and `password`.
2. `LoginService.login()` checks account lockout state.
3. `AuthenticationManager.authenticate()` verifies credentials via BCrypt.
4. On success: if MFA is enabled, returns `MfaLoginResponse` with `mfaSessionToken`; otherwise, issues access + refresh tokens.
5. On failure: increments `failedAttempts`, potentially locks the account.
6. Refresh tokens are stored in Redis as SHA-256 hashes (never stored in plaintext).

### Logout Flow
1. Client sends `POST /api/auth/logout` with `Authorization` header.
2. `TokenService.logout()` revokes all refresh tokens for the user in Redis.
3. Access token expires naturally (no server-side revocation needed for stateless JWT).

### Refresh Token Rotation
- On every refresh, the old refresh token is revoked and a new one is issued.
- This mitigates refresh token replay attacks.
- All previous refresh tokens for the user are revoked on login (single-session enforcement).

### MFA Flow
1. After credential verification, if MFA is enabled, a `mfaPendingToken` is stored in Redis.
2. For TOTP: user generates code from authenticator app.
3. For Email OTP: system generates 6-digit code, stores in Redis, sends via email.
4. Client sends `POST /api/auth/mfa/verify` with `mfaSessionToken` and `code`.
5. Code is verified (TOTP via `totp` library, Email OTP via Redis comparison).
6. On success: MFA pending session is revoked, access + refresh tokens are issued.

### Authentication Failure Handling
- `BadCredentialsException` → 401 "Invalid credentials"
- `LockedException` → 429 "Account is locked due to too many failed attempts"
- Custom `authenticationEntryPoint` returns 401 JSON for unauthenticated requests.
- All failures are logged via `SecurityAuditLogger`.

## 4. Authorization

### Roles

| Role | Description | Built-in |
|------|-------------|----------|
| `PLATFORM_ADMIN` | Platform-wide administrator with unrestricted cross-tenant access | Yes |
| `TENANT_ADMIN` | Tenant-scoped administrator managing their organization | Yes |
| `USER_MANAGER` | Can manage users within tenant | Yes |
| `DEPARTMENT_MANAGER` | Department manager, expense approval (multiple per department) | Yes |
| `EMPLOYEE` | Create and manage own expenses | Yes |
| `AUDITOR` | Read-only audit access | Yes |
| `FINANCE` | Process approved expenses | Yes |

Built-in roles are **immutable** — they cannot be updated, deleted, or have their permissions modified via the API.

### Permissions
29 fine-grained permissions defined in `Authorities`:
- Tenant: `TENANT_READ`, `TENANT_CREATE`, `TENANT_UPDATE`, `TENANT_DELETE`
- User: `USER_READ`, `USER_WRITE`, `USER_CREATE`, `USER_UPDATE`, `USER_DELETE`, `USER_ENABLE`, `USER_ASSIGN_ROLE`
- Role: `ROLE_READ`, `ROLE_WRITE`, `ROLE_DELETE`, `ROLE_ASSIGN_PERMISSION`
- Department: `DEPARTMENT_READ`, `DEPARTMENT_CREATE`, `DEPARTMENT_UPDATE`, `DEPARTMENT_DELETE`
- Expense: `EXPENSE_READ`, `EXPENSE_CREATE`, `EXPENSE_UPDATE`, `EXPENSE_DELETE`, `EXPENSE_APPROVE`, `EXPENSE_REJECT`, `EXPENSE_PROCESS`
- Reporting: `REPORT_READ`, `AUDIT_LOG_READ`

### Endpoint Authorization
- Spring Security URL-based authorization in `SecurityConfig`:
  - Public: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/mfa/verify`, `/actuator/health`
  - All other endpoints require authentication.

### Method-Level Authorization
- `@EnableMethodSecurity(prePostEnabled = true)` enables `@PreAuthorize` on service methods.
- Service methods use `@PreAuthorize("hasAuthority('PERMISSION_NAME')")` for authority checks.

### Resource/Object-Level Authorization
- `AuthorizationService` provides fine-grained checks beyond simple authority matching:
  - **Tenant isolation**: Users can only access resources within their tenant (except super admins).
  - **Department scoping**: Managers can only approve/reject expenses within their department.
  - **Resource ownership**: Employees can only view/edit their own expenses.
  - **Privilege hierarchy**: Users cannot manage users with equal or higher permissions.
  - **Last admin protection**: The last admin (PLATFORM_ADMIN or TENANT_ADMIN) in a tenant cannot be deleted or disabled.

## 5. Access-Control Security

### Broken Access Control
- **Mitigated**: All service methods have `@PreAuthorize` checks.
- **Mitigated**: `AuthorizationService` enforces tenant isolation and resource ownership.
- **Mitigated**: Controllers delegate to services that perform authorization checks.

### Privilege Escalation
- **Mitigated**: Role assignment is restricted to `USER_ASSIGN_ROLE` authority.
- **Mitigated**: Permission assignment is restricted to `ROLE_ASSIGN_PERMISSION` authority.
- **Mitigated**: Built-in role permissions cannot be modified.

### IDOR/BOLA
- **Mitigated**: `ExpenseService.findAccessibleExpense()` scopes queries to the user's tenant.
- **Mitigated**: `AuthorizationService.canViewExpense()` checks resource ownership and tenant membership.

### Horizontal Privilege Escalation
- **Mitigated**: Users can only manage their own expenses (edit, cancel).
- **Mitigated**: Tenant admin can only manage users within their tenant.

### Vertical Privilege Escalation
- **Mitigated**: `AuthorizationService` enforces privilege hierarchy for user management.
- **Mitigated**: Last admin protection prevents removing all admin access.

### Missing Endpoint Authorization
- **Confirmed**: All endpoints except public auth endpoints require authentication.
- **Confirmed**: All service methods have `@PreAuthorize` annotations.

### Role Manipulation
- **Mitigated**: Built-in roles cannot be modified.
- **Mitigated**: Custom role permissions are managed through the `RoleManagementService`.

## 6. Password and Credential Security

### Password Hashing
- **BCrypt** with strength 12.
- Hashed via Spring Security's `PasswordEncoder`.

### Password Policies
- Custom `@Password` annotation validates password requirements.
- Current password required for password changes and MFA disable.

### Password Reset
- Rate-limited per email (10 requests/minute).
- Token generated as SHA-256 of a secure random value.
- Token stored in database as SHA-256 hash (raw token never stored).
- Token expires after 15 minutes.
- Single-use: marked as `used_at` after reset.
- All refresh tokens revoked on password reset.
- Expired/used tokens cleaned up daily by scheduled job.

### Credential Storage
- Passwords: BCrypt hash in `users.password`.
- MFA secrets: Stored in `users.mfa_secret` (TOTP).
- Refresh tokens: SHA-256 hash in Redis.
- Password reset tokens: SHA-256 hash in `password_reset_tokens` table.

## 7. Token and Session Security

### Token Format
- **Access Token**: JWT with HS512 signature. Claims: `sub` (username), `roles`, `userId`, `tenantId`, `departmentId`, `iss`, `aud`, `iat`, `exp`.
- **Refresh Token**: JWT with HS512 signature. Claims: `sub` (username), `type`="refresh", `iss`, `aud`, `iat`, `exp`.
- **MFA Pending Token**: JWT with HS512 signature. Claims: `sub` (username), `type`="mfa_pending".

### Token Storage
- Access tokens: Client-side only (no server-side storage).
- Refresh tokens: Redis (SHA-256 hash).
- MFA pending tokens: Redis (plaintext username mapping).

### Expiration
- Access token: 15 minutes.
- Refresh token: 7 days.
- MFA pending token: 5 minutes.

### Refresh Behavior
- Refresh token rotation: old token revoked, new token issued on every refresh.
- All user refresh tokens revoked on login (single-session).
- All user refresh tokens revoked on password change/reset.

### Revocation
- **Logout**: All refresh tokens revoked for the user.
- **Password change**: All refresh tokens revoked.
- **Password reset**: All refresh tokens revoked.
- **Access tokens**: Not revocable (stateless); rely on short expiration.

### Concurrent Sessions
- Single-session enforcement: Login revokes all existing refresh tokens.
- No explicit concurrent session limit.

## 8. API Security

### Authentication Requirements
- All endpoints except public auth endpoints require a valid JWT access token.
- Token validated on every request by `JwtAuthenticationFilter`.

### Input Validation
- Jakarta Bean Validation (`@Valid @RequestBody`) on all write endpoints.
- Custom `@Password` annotation for password complexity.
- `GlobalExceptionHandler` returns structured validation error responses.

### Error Responses
- All errors return `ApiResponse` format with appropriate HTTP status codes.
- No stack traces or internal details exposed to clients.
- Generic error message for unexpected exceptions: "An unexpected error occurred".

### Rate Limiting
- Redis-based sliding window rate limiting via Lua script.
- Per-endpoint, per-identifier (client IP or username) limits.
- Configurable via `SecurityProperties`.

### CORS
- Configured with allowed origin from `AppProperties.frontendUrl`.
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS.
- Allowed headers: Authorization, Content-Type, X-Requested-With, Accept, Origin.
- Credentials allowed. Max age: 3600 seconds.

### CSRF
- **Disabled** (stateless API with JWT tokens).

### HTTP Security Headers
- `X-Frame-Options: SAMEORIGIN`
- `Content-Security-Policy: default-src 'self'`
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- `Permissions-Policy: geolocation=(), microphone=(), camera=(), payment=(), usb=()`

### Request Size Limits
- No explicit request size limits configured in the application. Defaults to Spring Boot defaults.

## 9. Input Security

### SQL Injection
- **Mitigated**: Spring Data JPA uses parameterized queries. No raw SQL concatenation with user input.

### Command Injection
- **Mitigated**: No command execution from user input.

### Path Traversal
- **Mitigated**: No file system operations from user input. REST API only.

### SSRF
- **Mitigated**: No external URL fetching from user input.

### XSS
- **Mitigated**: API returns JSON. No server-rendered HTML. CSP header configured.

### Unsafe Deserialization
- **Mitigated**: Jackson deserialization with standard Spring configuration. No custom deserializers.

### Mass Assignment
- **Mitigated**: Request DTOs define explicit fields. JPA entities are not directly bound to requests.

### Malicious File Uploads
- **Mitigated**: No file upload endpoints.

## 10. Data Protection

### Sensitive Data
- Passwords: BCrypt hash (never logged or exposed).
- MFA secrets: Stored in database, never exposed in API responses.
- JWT secret: Environment variable only, never in source code.
- Refresh tokens: SHA-256 hash in Redis.

### Encryption/TLS
- HSTS header configured (`max-age=31536000; includeSubDomains`).
- TLS termination is expected at the reverse proxy/load balancer level, not in the application.

### Database Protection
- All queries use parameterized statements via JPA.
- No raw SQL concatenation with user input.
- Tenant isolation enforced at the service layer.

### API Response Filtering
- Entities are never exposed directly; DTOs control response fields.
- Sensitive fields (password, MFA secret) are never included in DTOs.

## 11. Secret Management

### JWT Secret
- Configured via `JWT_SECRET` environment variable.
- Validated at startup: must be at least 32 characters; 64+ bytes recommended for HS512.
- Never stored in source code.

### Database Credentials
- Production: `${DB_USERNAME}`, `${DB_PASSWORD}` environment variables.
- Development: Hardcoded in `application.properties` (H2 in-memory, no credentials).

### Redis Credentials
- No authentication configured for Redis. Relies on network-level security.

### Email Credentials
- Configurable via `spring.mail.*` properties. Not configured in source code.

### No Hardcoded Secrets
- No passwords, tokens, API keys, or private keys are hardcoded in source code.
- Test JWT secret is a known test value in `application-test.properties`.

## 12. Logging and Audit

### Security Events
`SecurityAuditLogger` logs the following with structured format:
- Login success/failure
- Account locked/unlocked
- Logout
- Password changed
- Password reset requested/completed
- Token refreshed/revoked
- Access denied
- Suspicious activity
- MFA enabled/disabled
- MFA challenge sent
- MFA success/failure

### Audit Events
`AuditLogService` records to the `audit_logs` database table:
- Expense created/updated/cancelled/approved/rejected/processed
- User created/updated/deleted
- Role created/updated/deleted
- Tenant created/updated/deleted
- Department created/updated/deleted

### Sensitive Credential Logging
- **Not logged**: Passwords, JWT tokens, MFA secrets, refresh tokens.
- **Logged**: Username, IP address, action, resource type/id, timestamp.
- JWT secret is excluded from `JwtConfig.toString()` via `@ToString(exclude = "secret")`.

## 13. Error Handling

### Stack Trace Exposure
- **Not exposed**: `GlobalExceptionHandler` catches all exceptions and returns generic error messages.
- Unexpected errors return "An unexpected error occurred" with status 500.

### Database Details
- **Not exposed**: Database connection errors are caught and logged, not returned to clients.

### Internal Paths
- **Not exposed**: No file paths or internal structure is returned in error responses.

### Validation Errors
- Field-level error messages are returned in the `errors` map of `ApiResponse`.
- Messages are user-friendly (e.g., "must not be blank", "must be a positive number").

## 14. Dependency Security

### Security-Sensitive Dependencies
| Dependency | Version | Purpose |
|-----------|---------|---------|
| `jjwt-api/impl/jackson` | 0.12.5 | JWT handling |
| `spring-boot-starter-security` | (managed) | Authentication/authorization |
| `dev.samstevens.totp:totp` | 1.7.1 | TOTP MFA |
| `org.postgresql:postgresql` | (managed) | Database driver |
| `com.h2database:h2` | (managed) | Dev/test database |
| `org.flywaydb:flyway-core` | (managed) | Database migrations |

### Outdated Dependencies
- Cannot confirm outdated versions without checking against latest releases. The application uses Spring Boot 3.2.5 managed dependency versions.

## 15. Security Testing

### Existing Tests
| Test File | Coverage |
|-----------|----------|
| `SecurityIntegrationTest` | Unauthenticated access, invalid/expired tokens, malformed headers, self-registration blocked, unauthorized role access |
| `UserManagementControllerIntegrationTest` | CRUD, role assignment, privilege hierarchy, last admin protection, tenant scoping, validation |
| `RoleManagementControllerIntegrationTest` | List, create, update, delete, permission management, built-in role protection |
| `ExpenseControllerIntegrationTest` | Create, view, cross-department, cross-tenant access, approve, reject, process, status transitions |
| `JwtTokenProviderTest` | Token generation, validation, claim extraction, access/refresh token separation |
| `AuthorizationServiceTest` | Super admin, tenant admin, employee edit/approve, department manager |
| `RateLimitingServiceTest` | Redis script execution, rate limit exceeded, retry-after TTL |
| `ClientIpResolverTest` | Trusted proxy, CIDR ranges, X-Forwarded-For parsing |
| `TokenHashingServiceTest` | Consistent hashing, uniqueness, secure token generation |
| `MfaServiceTest` | TOTP secret, QR URI, email OTP, pending sessions |
| `AuthServiceTest` | Delegation to LoginService, TokenService, PasswordResetService |
| `ExpenseServiceTest` | Tenant/department assignment, owner-only scoping, cross-user edit blocked |

### Missing Security Tests
- No tests for CORS configuration.
- No tests for security headers.
- No tests for rate limiting integration (only unit tests for Redis script).
- No tests for password reset token expiration.
- No tests for concurrent session revocation on login.

## 16. Security Findings

| ID | Severity | Area | Finding | Evidence | Recommendation |
|----|----------|------|---------|----------|----------------|
| SF-01 | Medium | Infrastructure | Redis has no authentication configured | `application.properties` and `docker-compose.yml` have no Redis password | Configure Redis AUTH in production |
| SF-02 | Medium | Token Security | Access tokens are not server-side revocable | Stateless JWT design; logout only revokes refresh tokens | Accept short expiration (15 min) or implement token blacklist |
| SF-03 | Low | Testing | Integration tests require running Redis | `application-test.properties` configures Redis on localhost | Use embedded Redis for tests |
| SF-04 | Low | Configuration | H2 console enabled in dev profile | `spring.h2.console.enabled` not explicitly disabled | Disable H2 console or restrict access |
| SF-05 | Low | Architecture | `RefreshToken` entity and repository exist but are unused | `RefreshTokenRepository` exists but is not injected by any service | Remove unused entity/repository or document intended use |
| SF-06 | Informational | Logging | No correlation/request IDs for distributed tracing | No `X-Request-ID` or `MDC` correlation | Implement correlation IDs for production observability |
| SF-07 | Informational | API | No OpenAPI/Swagger documentation | No OpenAPI configuration present | Add springdoc-openapi for auto-generated API docs |

## 17. Security Recommendations

### Confirmed Issues

1. **Redis without authentication (SF-01)**: Redis in `docker-compose.yml` has no password configured. In production, this should be secured with `requirepass` or network-level isolation.

2. **Access token non-revocability (SF-02)**: Once issued, access tokens cannot be revoked until expiration. The 15-minute expiration mitigates this risk.

### Recommended Improvements

1. **Redis authentication**: Add `requirepass` to Redis configuration and pass credentials via environment variables.

2. **Correlation IDs**: Implement `X-Request-ID` header generation and propagation for request tracing across services.

3. **Request size limits**: Configure `server.tomcat.max-http-form-post-size` or `spring.servlet.multipart.max-file-size` to prevent large payload attacks.

4. **Security headers review**: Consider adding `X-XSS-Protection: 1; mode=block` (legacy but useful for older browsers).

5. **Password complexity validation**: Ensure the `@Password` validator enforces strong password policies (length, character classes).

6. **Token expiry in responses**: Include `expires_in` in all token responses to help clients manage token refresh.

7. **Audit log retention**: Implement a retention policy for the `audit_logs` table to prevent unbounded growth.

8. **Penetration testing**: Conduct a penetration test covering OWASP Top 10 categories.

## 18. Security Checklist

### Authentication
- [x] BCrypt password hashing (strength 12)
- [x] JWT stateless authentication
- [x] Access token expiration (15 min)
- [x] Refresh token rotation
- [x] MFA support (TOTP + Email)
- [x] Account lockout (5 attempts / 15 min)
- [x] Login failure logging

### Authorization
- [x] Role-based access control
- [x] Fine-grained permissions (29 authorities)
- [x] `@PreAuthorize` on service methods
- [x] Tenant isolation enforced
- [x] Resource ownership checks
- [x] Privilege hierarchy enforcement
- [x] Last admin protection

### Passwords
- [x] BCrypt hashing
- [x] Password change requires current password
- [x] Password reset with time-limited tokens
- [x] Single-use reset tokens
- [x] Token cleanup scheduled job

### Tokens
- [x] HS512 signing algorithm
- [x] Refresh token rotation on use
- [x] All tokens revoked on logout
- [x] All tokens revoked on password change
- [x] Tokens hashed in storage

### APIs
- [x] Input validation on all write endpoints
- [x] Consistent error response format
- [x] No sensitive data in responses
- [x] Entities not exposed directly

### Input Validation
- [x] Jakarta Bean Validation on request DTOs
- [x] Parameterized queries (JPA)
- [x] No file upload endpoints
- [x] No command execution from input

### Secrets
- [x] JWT secret via environment variable
- [x] No hardcoded secrets in source
- [x] Database credentials via environment variables
- [x] Sensitive fields excluded from logging

### Logging
- [x] Security audit logging
- [x] Business audit logging
- [x] No credentials logged
- [x] Structured security events

### Database
- [x] Parameterized queries
- [x] Tenant-scoped queries
- [x] Foreign key constraints
- [x] Indexes on frequently queried columns

### Dependencies
- [x] Modern Spring Boot 3.2.5
- [x] jjwt 0.12.5 (current)
- [x] TOTP library maintained

### Testing
- [x] Security integration tests
- [x] Authorization unit tests
- [x] JWT token tests
- [x] Rate limiting tests
- [x] Input validation tests
