# AI Agent Engineering Standards — Spring Boot Boilerplate

This file defines the engineering, security, and quality standards that AI coding agents **must** follow when working on this Spring Boot project. It is based on the actual repository structure, dependencies, and conventions already in place.

## 1. Project Context

Before making changes, verify these facts and preserve them unless there is a clear engineering reason to change them.

- **Framework / Language:** Spring Boot `3.2.5`, Java `21`.
- **Build Tool:** Maven (`pom.xml`, `mvnw` / `mvnw.cmd`).
- **Group / Artifact:** `com.example:demo:0.0.1-SNAPSHOT`.
- **Package Root:** `com.example.demo`.
- **Application Entry Point:** `com.example.demo.DemoApplication`.
- **Active Profiles:** default (`application.properties`), `prod` (`application-prod.properties`), `test` (`application-test.properties`).
- **Key Dependencies:**
  - Spring Web, Spring Security, Spring Data JPA, Spring Validation, Spring Data Redis, Spring Cache, Spring Mail, Spring Boot Actuator.
  - Flyway (`org.flywaydb:flyway-core`).
  - JJWT `0.12.5` (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`).
  - ModelMapper `3.2.0`.
  - TOTP `1.7.1` (`dev.samstevens.totp:totp`).
  - Lombok.
  - H2 (runtime, dev/test), PostgreSQL (runtime, prod).
- **Databases:**
  - Dev / test: H2 in-memory (`jdbc:h2:mem:...`).
  - Production: PostgreSQL (`jdbc:postgresql://...`).
- **Caching / Session Store:** Redis.
- **Migrations:** Flyway SQL scripts in `src/main/resources/db/migration/` (`V{version}__{description}.sql`).
- **No CI/CD pipelines are present** in this repo; verification is performed locally with Maven.

## 2. Engineering Principles

- **Clean, readable code.** Prefer explicit, self-documenting code over clever one-liners.
- **SOLID where appropriate** — especially Single Responsibility.
- **High cohesion, low coupling.** Keep related logic together; avoid tight coupling between layers.
- **Clear naming.** Classes are `PascalCase`; methods/variables are `camelCase`; constants are `UPPER_SNAKE_CASE`.
- **Small, focused methods and classes.** Avoid giant classes or giant methods.
- **Minimal duplication.** Extract repeated logic into well-named helpers or services.
- **Explicit dependencies.** Use constructor injection with `final` fields.
- **Testability.** Design code so it can be unit-tested with Mockito.
- **Simplicity over unnecessary abstraction.** Do not add layers, interfaces, or design patterns just for the sake of it.

### Avoid

- Over-engineering, premature abstraction, or speculative generality.
- Giant classes / giant methods / deep nesting.
- Magic numbers and magic strings; externalize them into named constants or `@ConfigurationProperties`.
- Global mutable state, unnecessary dependencies, dead code, commented-out code, debugging code.
- `@SneakyThrows`.

### Comments

- Comments should explain **why**, not merely describe **what**.
- If the code cannot be understood without a comment, refactor it first.

## 3. Architecture

This project follows a standard layered Spring Boot architecture:

```
Controller → Service → Repository → Database
```

### Controllers (`com.example.demo.controller`)

- Must be **thin**.
- Handle HTTP concerns only: request/response mapping, input validation (`@Valid`), and authentication context.
- Delegate all business logic to services.
- Return `ResponseEntity<ApiResponse<T>>` using the project's `ApiResponse` wrapper.
- Existing controllers: `AuthController` (`/api/auth`), `MfaController` (`/api/mfa`).

### Services (`com.example.demo.service`)

- Contain business logic and application orchestration.
- Annotated with `@Service`.
- Mark write methods with `@Transactional`.
- Mark read-only methods with `@Transactional(readOnly = true)`.
- Existing services: `AuthService` (orchestrator), `UserService`, `AccountLockoutService`, `TokenService`, `PasswordResetService`, `LoginService`, `MfaSetupService`, `MfaService`, `EmailService`.
- `AuthService` is a thin orchestrator that resolves client IP and dispatches to focused domain services; business logic lives in those domain services.

### Repositories (`com.example.demo.repository`)

- Contain persistence logic only.
- Extend `JpaRepository` and use Spring Data JPA derived query methods or `@Query`.
- Existing repositories: `UserRepository`, `RoleRepository`, `PasswordResetTokenRepository`.

### Entities (`com.example.demo.entity`)

- JPA-mapped domain objects only.
- Use Lombok `@Getter/@Setter` and `@Builder`; avoid `@Data` on entities to prevent `toString()`/`equals()` issues with lazy loading.
- Existing entities: `User`, `Role`, `PasswordResetToken`, `RefreshToken`, `MfaMethod`.

### DTOs (`com.example.demo.dto`)

- Used at API boundaries.
- Use Lombok (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`).
- Add Jakarta Bean Validation annotations for untrusted input.
- Never expose JPA entities directly through public APIs.
- Existing DTOs: `ApiResponse`, `RegisterRequest`, `LoginRequest`, `TokenResponse`, `UserResponse`, `MfaVerifyRequest`, `MfaSetupResponse`, etc.

### Configuration (`com.example.demo.config`)

- One concern per class.
- Use `@ConfigurationProperties` for grouped externalized properties (`AppProperties`, `JwtConfig`, `MfaProperties`, `SecurityProperties`).
- Existing: `SecurityConfig`, `CorsConfig`, `GlobalExceptionHandler`, `AppConfig`, `JwtConfig`, `MfaProperties`, `SecurityProperties`, `AppProperties`.

### Security (`com.example.demo.security`)

- `filter/` — servlet filters (`JwtAuthenticationFilter`).
- `jwt/` — `JwtTokenProvider`.
- `service/` — `CustomUserDetailsService`, `RefreshTokenService`, `TokenHashingService`, `ClientIpResolver`, `RateLimitingService`.
- `audit/` — `SecurityAuditLogger`.

### Validation (`com.example.demo.validation`)

- Custom validators live here. Reference: `Password` / `PasswordValidator`.

## 4. Dependency Injection

- **Prefer constructor injection** with `final` fields.
- Use Lombok `@RequiredArgsConstructor` when there are many dependencies.
- Avoid field injection (`@Autowired` on fields).
- Do not introduce static mutable dependencies or global state.

## 5. API Design

- Base path for application endpoints: `/api`.
- RESTful verbs and path naming as already established:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/mfa/verify`
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
  - `POST /api/auth/change-password`
  - `POST /api/auth/forgot-password`
  - `POST /api/auth/reset-password`
  - `POST /api/mfa/enable`
  - `POST /api/mfa/verify-setup`
  - `POST /api/mfa/disable`
  - `GET /api/mfa/status`
- Security path conventions:
  - `/api/public/**` → permit all.
  - `/api/user/**` → `USER` or `ADMIN`.
  - `/api/admin/**` → `ADMIN`.
  - `/actuator/health` → permit all; other `/actuator/**` → `ADMIN`.
- All controller responses must use `ApiResponse<T>`.
- Existing `ApiResponse` fields: `success`, `message`, `data`, `timestamp`.
- Use `ResponseEntity.ok(...)` for success; let `GlobalExceptionHandler` produce error HTTP status codes.
- Do not expose JPA entities through public APIs; use DTOs and ModelMapper.
- Do not create inconsistent response structures for individual endpoints.
- If adding list endpoints, use `Pageable` / `Page<T>` for large result sets.

## 6. Validation

- All untrusted input must be validated server-side.
- Use Jakarta Bean Validation on DTOs (`@NotBlank`, `@NotNull`, `@Size`, `@Email`, etc.).
- Apply `@Valid` on every `@RequestBody` parameter in controllers.
- Use the project's custom `@Password` validator on password fields (`RegisterRequest`, `ChangePasswordRequest`, `ResetPasswordRequest`).
- Cross-field / business-rule validation (e.g., password confirmation matching) belongs in the service layer.
- Never rely on client-side validation for security-sensitive checks.

## 7. Exception Handling

- Use the existing `GlobalExceptionHandler` (`com.example.demo.config.GlobalExceptionHandler`).
- Map exceptions to the existing status codes:
  - `MethodArgumentNotValidException` / `ConstraintViolationException` → `400 Bad Request` with field errors.
  - `BadCredentialsException` / `UsernameNotFoundException` → `401 Unauthorized`.
  - `LockedException` → `429 Too Many Requests`.
  - `AccessDeniedException` → `403 Forbidden`.
  - `IllegalArgumentException` → `400 Bad Request`.
  - `IllegalStateException` → `409 Conflict`.
  - Generic `Exception` → `500 Internal Server Error`.
- Error responses must use `ApiResponse.error(...)` and never include stack traces, SQL errors, internal class names, file paths, database details, or secrets.
- Do not catch broad exceptions unless the layer can handle them meaningfully.
- Never silently swallow exceptions.

## 8. Spring Security

Security is mandatory for every protected endpoint. The project uses the following mechanisms:

- **JWT-based stateless authentication.**
- **Spring Security filter chain** in `SecurityConfig`:
  - CSRF disabled (stateless JWT).
  - Session management set to `STATELESS`.
  - CORS sourced from `CorsConfig`.
  - Security headers: `X-Frame-Options`, CSP, `X-Content-Type-Options`, referrer policy, HSTS, `Permissions-Policy`.
- **JwtAuthenticationFilter** validates access tokens on every request except the public paths listed in `shouldNotFilter`.
- **Rate limiting** is enforced in the service layer via `RateLimitingService` using per-user limits in Redis; `RateLimitingFilter` was removed in favor of identity-based limits.
- **Role checks** are defined in `SecurityConfig` with `hasRole` / `hasAnyRole`; method security is enabled (`@EnableMethodSecurity`) for future use.

### JWT Requirements

- Secret sourced from `JWT_SECRET` environment variable or `jwt.secret` property; minimum 32 characters; HS512 recommends 64 bytes.
- Tokens include `issuer`, `audience`, `issuedAt`, `expiration`.
- Access tokens and refresh tokens are differentiated by a `type` claim (`refresh` for refresh tokens).
- Validate signature, expiration, issuer, audience, and required claims.
- Do not trust unvalidated client-provided identity or authorization information.

### Token Storage & Lifecycle

- Refresh tokens are **hashed** with `TokenHashingService` (SHA-256) before storage in Redis.
- Refresh tokens are **rotated** on each use and the old one is revoked.
- Revoke all user refresh tokens on logout, password change, and password reset.
- Password reset tokens are single-use, expire in 15 minutes, and are hashed.

### MFA

- Supported methods: `TOTP` and `EMAIL`.
- TOTP secrets are generated with `DefaultSecretGenerator`.
- Email OTPs are stored in Redis with a TTL.
- MFA pending session tokens are stored in Redis with a TTL.

### Audit & Logging

- Log security events through `SecurityAuditLogger`.
- Never log tokens, passwords, secrets, or full request bodies.
- Security-sensitive changes must include tests.

## 9. OWASP Security

Align all security work with relevant OWASP guidance and the project's existing controls:

- **Broken Access Control:** Enforce authorization server-side in `SecurityConfig` and service methods. Never rely on frontend authorization.
- **Cryptographic Failures:** Use BCrypt (strength 12) for passwords, SHA-256 for token hashing, HS512 for JWT, and `SecureRandom` for secure tokens. Never hardcode secrets.
- **Injection:** Use Spring Data JPA with parameterized queries; do not concatenate untrusted input into SQL.
- **Insecure Design:** Use DTOs, rate limiting, account lockout, MFA, and token rotation.
- **Security Misconfiguration:** Externalize secrets; disable H2 console and lock down Actuator in production.
- **Vulnerable and Outdated Components:** Check dependency versions and known CVEs before adding dependencies.
- **Authentication Failures:** Implement account lockout, failed-attempt tracking, and brute-force/rate limiting.
- **Software/Data Integrity Failures:** Verify JWT signatures; do not bypass validation.
- **Security Logging/Monitoring Failures:** Use `SecurityAuditLogger` for all security events.
- **SSRF:** Validate and sanitize any URLs constructed from user input.
- Also consider: SQL Injection, XSS (output encoding/headers), CSRF (stateless JWT), Path Traversal, Command Injection, XXE, Insecure Deserialization, Mass Assignment, IDOR/BOLA, and Rate Limiting.

### Never

- Hardcode secrets, passwords, or credentials.
- Disable TLS verification.
- Bypass authorization.
- Disable security controls to make tests pass.
- Expose stack traces, internal errors, tokens, or PII in API responses.

## 10. Database and JPA

- Use **H2** for dev/tests and **PostgreSQL** for production.
- Use **Flyway** for all schema migrations (`src/main/resources/db/migration/`).
- `spring.jpa.hibernate.ddl-auto=validate` (dev/test) and `none` (prod).
- Keep entities in `com.example.demo.entity`; use JPA annotations and Lombok.
- Keep DTOs separate from entities.
- Use lazy loading by default; avoid unnecessary eager loading.
- Avoid N+1 queries — use `JOIN FETCH` or `@EntityGraph` where needed.
- Use pagination for large result sets.
- Never concatenate untrusted input into SQL. Use parameterized queries or the JPA criteria / repository abstraction.
- Migration scripts must be idempotent and versioned sequentially.

## 11. Transactions

- Mark write operations with `@Transactional`.
- Mark read-only queries with `@Transactional(readOnly = true)`.
- Keep transaction boundaries clear and small.
- Avoid external HTTP calls (e.g., sending email, calling third-party APIs) inside long-running database transactions unless the outcome must be tied to the transaction.
- Avoid transactions spanning unrelated operations.

## 12. Performance

- Do not optimize based on assumptions.
- Profile and measure before making performance changes.
- Watch for N+1 queries, excessive database calls, network calls, and large in-memory collections.
- Use Redis caching (`@Cacheable`, etc.) for appropriate data, with sensible TTLs.
- Use `Pageable` for list endpoints.
- Do not sacrifice correctness or security for performance.

## 13. Logging and Observability

- Use SLF4J via Lombok `@Slf4j`.
- Use `SecurityAuditLogger` for security-relevant events.
- Use appropriate log levels (`DEBUG` for dev, `INFO`/`WARN`/`ERROR` for prod).
- Never log:
  - Passwords, tokens (access/refresh/MFA/session), API keys, client secrets.
  - Database credentials, private keys.
  - Sensitive personal or customer information unless explicitly required and masked.
- Use structured, consistent log messages for security auditing.
- Actuator + Prometheus metrics are exposed for observability (prod: limited to authorized access).

## 14. Configuration and Secrets

- Externalize all configuration and secrets through `application.properties` / `application-{profile}.properties` and environment variables.
- Use `@ConfigurationProperties` for grouped settings (`AppProperties`, `JwtConfig`, `MfaProperties`, `SecurityProperties`).
- Required external secrets for production:
  - `JWT_SECRET`
  - `DB_USERNAME`, `DB_PASSWORD`
  - `REDIS_HOST`, `REDIS_PORT` (defaults provided)
  - `BASE_URL`, `FRONTEND_URL` (defaults provided)
- Never hardcode secrets, JWT keys, database credentials, or API keys in source files.
- Never commit secrets to source control.
- Test-only secrets in `application-test.properties` are acceptable if they are clearly non-production values.

## 15. External Integrations

- The project uses Spring Mail (`EmailService`) for transactional emails.
- For any new external API or service integration, consider:
  - Connection and read timeouts.
  - Error handling and retry behavior.
  - Circuit breakers where appropriate.
  - Idempotency.
  - Authentication and response validation.
  - Sensitive data handling.
- Do not retry blindly; do not retry non-idempotent operations without safeguards.

## 16. Testing

Every meaningful change must include appropriate tests.

- **Unit tests:** Service layer with JUnit 5, Mockito, and AssertJ. Use `@ExtendWith(MockitoExtension.class)`. Reference existing tests in `src/test/java/com/example/demo/service/`.
- **Integration tests:** Use `@SpringBootTest` with `@ActiveProfiles("test")` and H2. Use `MockMvc` for controller tests.
- **Security tests:** Test authentication/authorization, permitted/denied endpoints, invalid/expired tokens, and role checks.
- **Repository tests:** Use `@DataJpaTest` with H2 where appropriate.
- Use `@Nested` and `@DisplayName` for organized, readable tests.
- Use builders or factory methods for test data; avoid inline duplication.
- Critical security paths must test:
  - Unauthenticated access.
  - Unauthorized / forbidden access.
  - Invalid and expired tokens.
  - Invalid input / validation failures.
  - Resource ownership violations where applicable.
- Do not remove or weaken tests to make implementation pass.
- Tests should verify behavior, not implementation details.

### Existing Test Files

- `DemoApplicationTests`
- `AuthServiceTest`
- `MfaServiceTest`
- `ClientIpResolverTest`
- `RateLimitingServiceTest`
- `JwtTokenProviderTest`
- `TokenHashingServiceTest`

### Verification Commands

```bash
# Windows
mvnw.cmd clean compile
mvnw.cmd test

# Unix / WSL
./mvnw clean compile
./mvnw test
```

## 17. Dependency Management

Before adding a new dependency:

1. Check whether existing project functionality can solve the problem.
2. Determine whether the dependency is actually necessary.
3. Check maintenance status and release cadence.
4. Check known vulnerabilities.
5. Verify compatibility with Spring Boot `3.2.5` and Java `21`.
6. Consider licensing.
- Avoid dependencies for trivial functionality.
- Prefer managed Spring Boot dependency versions where available.

## 18. Refactoring

- Prefer maintainable code over blindly copying existing patterns.
- Do not perform unrelated large refactors.
- Do not change working behavior without justification.
- Do not introduce unnecessary abstractions.
- Do not rewrite entire modules for small feature requests.
- Preserve behavior unless the task explicitly requires behavioral changes.

## 19. Agent Workflow

For every meaningful coding task, follow this workflow.

### Step 1 — Understand

- Read this `AGENTS.md`.
- Inspect the existing implementation and related modules.
- Inspect existing tests and the Flyway migrations.
- Identify dependencies, business rules, API/database impacts, and security implications.
- Do not immediately start coding.

### Step 2 — Plan

Create a concise implementation plan containing:

- Files/components to change and why.
- Expected behavior.
- Security considerations.
- Testing strategy.
- Potential risks.

### Step 3 — Implement

- Implement the smallest clean solution that satisfies the requirement.
- Follow the existing architecture and conventions.
- Do not modify unrelated code.

### Step 4 — Test

Run the project's build and tests:

```bash
mvnw.cmd clean compile
mvnw.cmd test
```

Use the existing Maven wrapper; do not invent replacement commands unless the build itself is broken.

### Step 5 — Review

Review the final diff for:

- Correctness.
- Security vulnerabilities and authorization issues.
- Missing validation and exception handling.
- Sensitive logging.
- Performance problems.
- Duplicate logic.
- Unnecessary dependencies.
- Backward compatibility.
- Accidental changes.

### Step 6 — Final Verification

Before declaring the task complete:

- Build succeeds.
- Relevant tests pass.
- No secrets were introduced.
- No debugging code remains.
- No unrelated files were changed.
- The final diff has been reviewed.

## 20. Forbidden Shortcuts

The agent MUST NOT:

- Disable authentication to make functionality work.
- Disable authorization to make tests pass.
- Disable TLS verification.
- Hardcode credentials or secrets.
- Commit secrets.
- Log credentials or tokens.
- Expose stack traces or sensitive internal errors.
- Concatenate untrusted input into SQL.
- Remove security checks without explicit justification.
- Delete tests to make CI pass.
- Modify tests solely to hide defects.
- Introduce unnecessary dependencies.
- Rewrite unrelated modules.
- Perform large refactors for small feature requests.
- Suppress warnings without understanding them.

## 21. Definition of Done

A change is complete only when:

- The requirement is correctly implemented.
- The implementation follows project architecture.
- Code is clean and maintainable.
- Input is appropriately validated.
- Authorization is correctly enforced.
- Security implications were reviewed.
- Appropriate tests exist.
- Relevant tests pass.
- The project builds successfully (`mvnw.cmd compile` / `mvnw.cmd test`).
- No secrets are exposed.
- Logging does not expose sensitive data.
- No unnecessary dependencies were introduced.
- No unrelated code was changed.
- The final diff was reviewed.

If a requirement conflicts with security, correctness, or maintainability, do not silently implement an unsafe solution. Explain the conflict and propose a safe alternative.
