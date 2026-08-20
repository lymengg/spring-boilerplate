# AI Agent Engineering Standards — Spring Boot Boilerplate

## Tech Stack

- Java 21, Spring Boot 3.2.5, Maven
- Spring Data JPA + Flyway (H2 dev/test, PostgreSQL prod)
- Spring Security (JWT stateless, method-level `@PreAuthorize`)
- Lombok, ModelMapper 3.2.0, JJWT 0.12.5, TOTP 1.7.1
- Redis (cache), Spring Mail, Actuator + Prometheus
- JUnit 5 + Mockito + AssertJ

## Package Layout

```
com.example.demo
├── controller    — REST controllers (thin, HTTP only)
├── service       — service interfaces
│   └── impl       — service implementations (XxxServiceImpl)
├── repository    — Spring Data JPA repositories
├── entity        — JPA entities
├── dto           — request/response DTOs
├── mapper        — entity-to-DTO mappers (@Component)
├── constants     — AuditActions, Roles, UserPermission
├── config        — SecurityConfig, AppConfig, GlobalExceptionHandler
├── security      — JWT, filters, auth services, audit logger
│   ├── audit      — SecurityAuditLogger
│   ├── filter     — JwtAuthenticationFilter
│   ├── jwt        — JwtTokenProvider
│   └── service    — AuthorizationService, CustomUserDetailsService, etc.
└── validation    — custom validators (e.g., @Password)
```

## Naming Conventions

| Artifact | Pattern | Example |
|---|---|---|
| Service interface | `XxxService` | `ExpenseService` |
| Service impl | `XxxServiceImpl` in `service/impl/` | `ExpenseServiceImpl` |
| Request DTO | `XxxRequest` (not `XxxRequestDto`) | `ExpenseCreateRequest` |
| Response DTO | `XxxResponse` (not `XxxResponseDto`) | `ExpenseResponse` |
| Mapper | `XxxMapper` | `ExpenseMapper` |
| Repository | `XxxRepository` | `ExpenseRepository` |
| Entity | singular noun, `@Table(name = "plural_snake_case")` | `Expense` → `expenses` |
| Controller | `XxxController` | `ExpenseController` |
| Unit test | `XxxServiceTest` | `ExpenseServiceTest` |
| Integration test | `XxxControllerIntegrationTest` | `ExpenseControllerIntegrationTest` |
| Flyway migration | `V{n}__{description}.sql` | `V14__add_invoice_table.sql` |

## Architecture

```
Controller → Service → Repository → Database
```

### Dependency Rules (MUST follow)

- A service **MUST** only inject its **own** repository.
- A service **MUST NOT** inject another domain's repository — delegate to that domain's service instead.
  - Example: `ApprovalService` saves an `Expense` → `ExpenseService.save()`, not `ExpenseRepository.save()`.
  - Example: `ExpenseService` needs a `Department` → `DepartmentManagementService.findById()`, not `DepartmentRepository.findById()`.
- **Exception**: Direct repository injection is acceptable only to avoid circular dependencies, and **MUST** be documented with a comment.

```java
// BAD — ApprovalService injects ExpenseRepository directly
private final ExpenseRepository expenseRepository;
Expense approved = expenseRepository.save(expense);

// GOOD — ApprovalService delegates to ExpenseService
private final ExpenseService expenseService;
Expense approved = expenseService.save(expense);
```

### Controllers

- **MUST** be thin — HTTP concerns only, delegate to services.
- **MUST** return `ResponseEntity<ApiResponse<T>>`.
- **MUST** use `@Valid` on every `@RequestBody`.
- **MUST** accept `Authentication` parameter when the current user is needed — never extract from `SecurityContextHolder` directly.
- **MUST** use `@RequiredArgsConstructor` with `final` service fields.
- Base path pattern: `/api/{domain}` (e.g., `/api/expenses`, `/api/management/users`).

### Entities

- **MUST** use `@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor/@Builder` — never `@Data` on entities.
- **MUST** use `@CreationTimestamp`/`@UpdateTimestamp` for audit timestamps.
- **MUST** use `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- **MUST** use `FetchType.LAZY` for `@ManyToOne` and `@ManyToMany` relationships.
- **MUST NOT** be exposed through APIs — use DTOs.
- Domain logic methods on entities are permitted (e.g., `User.lockAccount()`, `User.unlockIfExpired()`).

### DTOs

- Request DTOs: `@Data/@Builder/@NoArgsConstructor/@AllArgsConstructor` with Jakarta Validation annotations.
- Response DTOs: `@Data/@Builder/@NoArgsConstructor/@AllArgsConstructor`, no validation annotations.
- **MUST** reuse `ApiResponse<T>` for all responses — never create a new wrapper.
- Use `ApiResponse.success(message, data)` for success, `ApiResponse.error(message)` for errors.

### Mappers

- **MUST** be `@Component` classes in `com.example.demo.mapper`.
- **MUST** have a `toResponse(Entity)` method returning the response DTO.
- Mapping logic **MUST NOT** be inline in services or controllers.
- When a new DTO response is introduced, a corresponding mapper **MUST** be created immediately — not deferred.
- Manual mapping is the project convention (not ModelMapper), despite the `ModelMapper` bean existing in `AppConfig`.

### Constants

- Permissions, audit actions, roles, resource types **MUST** live in `com.example.demo.constants`.
- `UserPermission` is an `enum` — the single source of truth for all permission names. It is used in JPA (`Role.permissions`), DTOs (`RolePermissionRequest`, `RoleResponse`), and its `.name()` values are matched at runtime by Spring Security.
- `AuditActions` and `Roles` **MUST** be `final` classes with `private` constructor and `public static final String` fields.
- **MUST NOT** use inline string literals for repeated values — use `UserPermission` enum or `Roles`/`AuditActions` constants.
- Authority strings in `@PreAuthorize("hasAuthority('...')")` SpEL expressions **MUST** match a `UserPermission` enum name (e.g., `'EXPENSE_APPROVE'` matches `UserPermission.EXPENSE_APPROVE`).
- **MUST NOT** create a separate `Authorities` string-constants class — `UserPermission` enum is the single source of truth.

## Code Style

- Java 21, Spring Boot 3.2.5, Maven.
- Constructor injection with `final` fields. Use `@RequiredArgsConstructor` for many deps.
- No field injection. No `@SneakyThrows`. No `@Data` on entities (use `@Getter/@Setter/@Builder`).
- `@Transactional` on writes, `@Transactional(readOnly = true)` on reads.
- No magic strings/numbers — externalize to constants or `@ConfigurationProperties`.
- No dead code, no commented-out code, no debugging code.
- Comments explain **why**, not **what**.

## Error Handling

- **No custom exception classes** — the project uses standard exceptions:
  - `IllegalArgumentException` → 400 Bad Request (validation, not-found, conflict on business rules).
  - `IllegalStateException` → 409 Conflict (invalid state transition, e.g., approving an already-rejected expense).
  - `AccessDeniedException` → 403 Forbidden (ownership/tenant violations).
  - `BadCredentialsException` / `UsernameNotFoundException` → 401 Unauthorized.
  - `LockedException` → 429 Too Many Requests.
  - `DataIntegrityViolationException` → 400 Bad Request (unique constraint violations).
- Services **MUST** throw these exceptions — `GlobalExceptionHandler` catches and formats them.
- **MUST NOT** return `null` for not-found — throw `IllegalArgumentException` with a descriptive message.
- **MUST NOT** catch broad exceptions unless the layer handles them meaningfully (e.g., catching `DataIntegrityViolationException` to provide a user-friendly message).
- **MUST NOT** expose stack traces or internal errors to clients — `GlobalExceptionHandler` handles the generic `Exception` catch-all.

## Security

### Authentication & Authorization

- JWT stateless auth. Method security via `@PreAuthorize("hasAuthority('...')")` on service methods.
- `AuthorizationService` handles ownership, tenant, and department checks — inject it, do not replicate its logic.
- **MUST** use `AuthorizationService` for: tenant access (`canAccessTenant`), department access (`canAccessDepartment`), resource ownership (`isResourceOwner`), domain-specific checks (`canViewExpense`, `canApproveExpense`, etc.).
- **MUST NOT** bypass auth or disable security for tests.

### Multi-Tenancy

- Every tenant-scoped query **MUST** filter by `tenant_id`.
- Super admin = no tenant + `PLATFORM_ADMIN` role — bypasses tenant checks via `AuthorizationService.isSuperAdmin()`.
- Services **MUST** resolve the current user's tenant and scope queries accordingly.
- Cross-tenant access **MUST** throw `AccessDeniedException` or return empty results (for list endpoints).

### Secrets & Logging

- Never hardcode secrets. Never log tokens, passwords, or PII.
- Use `SecurityAuditLogger` for security events (login, logout, MFA, token refresh, account lock).
- Use `AuditLogService.record()` for business audit (user created, expense approved, etc.).
- Audit log calls **MUST** use constants from `AuditActions` for action, resource type, and resource ID.

### Input Validation

- All untrusted input **MUST** be validated with Jakarta Bean Validation (`@Valid` on every `@RequestBody`).
- Custom validators go in `com.example.demo.validation` (e.g., `@Password`).
- Validation messages **MUST** be human-readable (e.g., "Username is required", not "must not be blank").

### Token Management

- Refresh tokens hashed (SHA-256), rotated on use, revoked on logout/password change.
- Password reset tokens hashed, single-use, expire after configured TTL.

## Database

- H2 for dev/test, PostgreSQL for prod.
- Flyway migrations in `src/main/resources/db/migration/` (`V{version}__{description}.sql`).
- `ddl-auto=validate` (dev/test), `none` (prod).
- **MUST** check existing migrations for the highest version number. Next migration increments by 1.
- **MUST NEVER** modify a previously-applied migration — always create a new one.
- Parameterized queries only — never concatenate untrusted input into SQL.
- Use `Pageable` for list endpoints. Avoid N+1 queries (use `@EntityGraph` or explicit fetch joins when needed).
- Column naming: `snake_case` in DDL, `camelCase` in Java with `@Column(name = "...")` mapping.
- **MUST** add indexes for foreign keys and frequently-filtered columns (see `Expense` entity for pattern).

## Reuse Before Create

Before creating any new class, **MUST** search for an existing one that serves the same purpose:
- `ApiResponse<T>` — reuse, do not create new response wrappers.
- `AuthorizationService` — reuse for all authz checks.
- `SecurityAuditLogger` / `AuditLogService` — reuse for all audit logging.
- Existing mappers — extend before creating a new mapper for the same entity.
- Existing DTOs — check if an existing `XxxRequest`/`XxxResponse` already covers the use case.
- `UserService` — reuse for user lookups (`getByUsername`, `getById`, `save`), do not inject `UserRepository` directly.

## Testing

### Unit Tests

- Location: `src/test/java/com/example/demo/service/`
- Naming: `XxxServiceTest`
- Pattern: `@ExtendWith(MockitoExtension.class)`, `@Mock` for dependencies, `@InjectMocks` for the service under test.
- Assertions: AssertJ (`assertThat`, `assertThatThrownBy`).
- **MUST** use `@DisplayName` for human-readable test names.
- **MUST** cover: happy path, edge cases, authorization failures, validation failures.

### Integration Tests

- Location: `src/test/java/com/example/demo/controller/`
- Naming: `XxxControllerIntegrationTest`
- Pattern: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Transactional`.
- **MUST** generate real JWT tokens via `JwtTokenProvider` and `CustomUserDetailsService` (see `ExpenseControllerIntegrationTest` for the pattern).
- **MUST** cover: unauthenticated access (401), unauthorized access (403), validation failures (400), state conflicts (409), cross-tenant access blocked, ownership violations.
- **MUST NOT** delete or weaken tests to make implementation pass.

### Test Command
```bash
# Windows
mvnw.cmd clean test

# Linux/macOS
./mvnw clean test
```

## Agent Workflow

### 1. Understand
- Read this file. Inspect existing code, tests, and migrations. Do not immediately start coding.
- Search for existing classes that may already solve part of the task.

### 2. Plan — Impact List
Before writing code, enumerate every file affected:
- **New files**: entities, DTOs, mappers, repositories, services, controllers, migrations, constants.
- **Modified files**: services adding deps, controllers wiring endpoints, security config for new paths.
- **Import updates**: all files referencing moved or renamed classes.
- **Test files**: new or updated test classes.

### 3. Implement
- Smallest clean solution. Follow existing conventions. Do not modify unrelated code.
- Create files in the correct package with correct naming.
- Add new permissions to `UserPermission` enum and constants to `AuditActions` / `Roles` before referencing them.
- Register new public endpoints in `SecurityConfig` if they need to be permit-all (default is authenticated).

### 4. Test
```bash
mvnw.cmd clean test
```

### 5. Review
Check diff for: correctness, security, missing validation, sensitive logging, duplicate logic, unnecessary deps, backward compatibility, tenant isolation, missing audit log calls.

## Known Pitfalls

- **Forgetting to register permit-all endpoints**: New public endpoints (e.g., password reset) **MUST** be added to `SecurityConfig.requestMatchers().permitAll()`. Default is `.anyRequest().authenticated()`.
- **Forgetting to add permissions**: New permissions **MUST** be added to `UserPermission` enum and seeded in a Flyway migration (via `role_permissions` table).
- **Exposing entities in responses**: Always map through a mapper to a response DTO.
- **N+1 queries**: Lazy-loaded relationships accessed during response mapping cause N+1. Use `@EntityGraph` or fetch joins.
- **Missing tenant scoping**: Every query that returns tenant-scoped data **MUST** filter by `tenant_id` unless the user is super admin.
- **Missing audit log**: Business-affecting actions (create, update, delete, approve, reject) **MUST** call `AuditLogService.record()`.
- **Inline mapping**: Do not map entities to DTOs inline in services — always use a mapper `@Component`.

## Forbidden

- Disable auth/authz to make things work.
- Hardcode secrets or credentials.
- Log credentials or tokens.
- Expose stack traces or internal errors.
- Concatenate untrusted input into SQL.
- Delete tests to pass CI.
- Introduce unnecessary dependencies.
- Perform large refactors for small requests.
- Create custom exception classes (use standard exceptions — see Error Handling).
- Use `@Data` on entities (use `@Getter/@Setter/@Builder`).
- Use field injection (use constructor injection with `final` fields).
- Use `@SneakyThrows`.
- Leave dead code, commented-out code, or debugging code.

## Definition of Done

- Requirement correctly implemented.
- Follows project architecture and naming conventions.
- Input validated. Authorization enforced. Tenant isolation verified.
- Audit log calls present for business actions.
- Tests exist and pass. Build succeeds.
- No secrets exposed. No sensitive logging.
- No unrelated code changed. Diff reviewed.

If a requirement conflicts with security, correctness, or maintainability, do not silently implement an unsafe solution. Explain the conflict and propose a safe alternative.
