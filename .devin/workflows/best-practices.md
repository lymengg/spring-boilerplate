---
description: Security, code quality, and general best practices for the Spring Boot project
---

# Best Practices

Shared standards that all agents (reviewer, planner, builder) must follow and enforce.

---

## Security Best Practices

### Authentication & Authorization
- Use JWT with short-lived access tokens (15 min max) and long-lived refresh tokens.
- Store refresh tokens hashed (never plaintext) — use `TokenHashingService`.
- Rotate refresh tokens on each use (detect replay attacks).
- Revoke refresh tokens on logout and password change.
- Use `@PreAuthorize` / `@PostAuthorize` for method-level security where needed.
- Never expose internal IDs or sensitive fields in API responses — use dedicated response DTOs.

### Password Security
- Hash passwords with BCrypt (cost factor ≥ 12).
- Enforce strong password policy via `@Password` custom validator.
- Never log or return password fields in any response.
- Password reset tokens must be single-use and expire within 15 minutes.

### API Security
- Validate all inputs with `@Valid` and Bean Validation annotations.
- Use `@RequestBody` with DTOs — never bind directly to entities.
- Enforce rate limiting on auth endpoints (login, register, password reset) via `RateLimitingFilter`.
- Apply CORS restrictions per environment (never `*` in production).
- Disable H2 console and actuator sensitive endpoints in production.
- Use HTTPS in production; redirect HTTP to HTTPS.

### Secrets & Configuration
- Never hardcode secrets, keys, or credentials in source code.
- Use environment variables or external config servers for sensitive values.
- Use `@ConfigurationProperties` (not `@Value`) for grouped configuration.
- Keep `application.yml` profiles separate (`dev`, `prod`, `test`).
- Add sensitive keys to `.gitignore` or use placeholder values with env var substitution.

### SQL & Data Security
- Use Spring Data JPA derived queries or `@Query` with parameterized JPQL — never string concatenation.
- Use Flyway for all schema migrations — never `ddl-auto=update` in production.
- Enforce `@Transactional(readOnly = true)` for read operations.
- Avoid exposing entity relationships that could cause data leakage (use DTO projection).

### Logging & Auditing
- Log security events (login success/failure, token refresh, password change) via `SecurityAuditLogger`.
- Never log sensitive data: passwords, tokens, PII, or full request bodies.
- Use structured logging for machine-parseable audit trails.
- Configure log levels per environment (DEBUG in dev, WARN+ in prod).

---

## Code Quality Best Practices

### Structure & Layers
- **Controllers**: Thin layer only — delegate to services. Return `ApiResponse` wrapper.
- **Services**: Business logic only. Use `@Service` + `@Transactional`. No HTTP concerns.
- **Repositories**: Data access only. Extend `JpaRepository`. Custom queries in `@Query`.
- **Entities**: JPA-mapped domain objects. No business logic. No serialization concerns.
- **DTOs**: Transfer objects for API boundaries. Keep separate from entities.
- **Config**: All configuration in `config/` package. One concern per class.

### Naming Conventions
- Classes: `PascalCase` — e.g., `UserService`, `AuthController`.
- Methods: `camelCase` — verbs for actions (`findByEmail`, `createUser`).
- Constants: `UPPER_SNAKE_CASE`.
- Packages: all lowercase, singular — `entity`, `dto`, `service`.
- DTOs: suffix with purpose — `LoginRequest`, `UserResponse`, `TokenResponse`.

### Dependency Injection
- Use **constructor injection** (not `@Autowired` field injection).
- Use `final` fields for injected dependencies.
- Use Lombok `@RequiredArgsConstructor` for classes with many dependencies.

### Error Handling
- Use `@ControllerAdvice` via `GlobalExceptionHandler` for centralized exception handling.
- Throw domain-specific exceptions (not generic `RuntimeException`).
- Return consistent `ApiResponse` error format with error code, message, and timestamp.
- Never expose stack traces or internal errors to clients.

### Lombok Usage
- Use `@Getter`, `@Setter`, `@RequiredArgsConstructor` — avoid `@Data` on JPA entities (can cause lazy loading issues).
- Use `@Builder` for complex object construction.
- Use `@Slf4j` for logging instead of manual logger declarations.
- Do not use `@SneakyThrows` — handle exceptions explicitly.

### ModelMapper
- Configure mapping explicitly for complex field mappings.
- Validate mappings in tests to catch misconfigured fields.
- Use `@Mapping`-style configuration where possible for clarity.

---

## General Best Practices

### Testing
- **Unit tests**: Service layer with mocked dependencies. Name: `MethodName_Scenario_ExpectedResult`.
- **Integration tests**: Controller layer with `@SpringBootTest` + `MockMvc` + H2.
- **Security tests**: Use `@WithMockUser` and `SecurityMockMvcRequestPostProcessors` for role-based tests.
- **Test isolation**: Each test should be independent — use `@Transactional` rollback or `@DirtiesContext`.
- **Coverage**: Aim for ≥ 80% on service and security layers. Critical paths must have tests.
- **Test data**: Use builders or factories. Avoid inline test data duplication.

### Performance
- Use `@Transactional(readOnly = true)` for read-only operations (enables Hibernate optimizations).
- Use Redis cache for frequently accessed, rarely changing data. Set appropriate TTL.
- Avoid N+1 queries — use `@EntityGraph` or `JOIN FETCH` where needed.
- Use pagination for list endpoints (`Pageable`).
- Use `@Async` only for fire-and-forget operations (e.g., sending emails).
- Monitor with Actuator + Micrometer/Prometheus metrics.

### API Design
- Use RESTful conventions: `GET` (read), `POST` (create), `PUT` (full update), `PATCH` (partial update), `DELETE`.
- Use plural nouns for resource paths: `/api/users`, `/api/auth/login`.
- Return appropriate HTTP status codes (200, 201, 204, 400, 401, 403, 404, 409, 500).
- Version APIs via path prefix: `/api/v1/...`.
- Use consistent response envelope (`ApiResponse`) for all endpoints.

### Git & Commits
- Use conventional commit messages: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- Keep commits atomic — one logical change per commit.
- Never commit secrets, `.env` files, or IDE-specific configs.
- Squash merge feature branches into main.
