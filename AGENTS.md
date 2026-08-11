# AI Agent Engineering Standards — Spring Boot Boilerplate

## Architecture

```
Controller → Service → Repository → Database
```

```
com.example.demo
├── controller    — REST controllers (thin, HTTP only)
├── service       — business logic, @Service
├── repository    — Spring Data JPA repositories
├── entity        — JPA entities (@Getter/@Setter/@Builder)
├── dto           — request/response DTOs (validated at API boundary)
├── mapper        — entity-to-DTO mappers (@Component)
├── constants     — named constants (Authorities, AuditActions)
├── config        — Spring config, SecurityConfig, GlobalExceptionHandler
├── security      — JWT, filters, auth services, audit logger
└── validation    — custom validators
```

**MUST follow:**

- A service **MUST** only inject its **own** repository.
- A service **MUST NOT** inject another domain's repository — delegate to that domain's service instead.
  - Example: `ApprovalService` saves an `Expense` → `ExpenseService.save()`, not `ExpenseRepository.save()`.
  - Example: `ExpenseService` needs a `Department` → `DepartmentManagementService.findById()`, not `DepartmentRepository.findById()`.
- **Exception**: Direct repository injection is acceptable only to avoid circular dependencies, and **MUST** be documented with a comment.

**Bad vs Good — cross-domain dependency:**
```java
// BAD — ApprovalService injects ExpenseRepository directly
private final ExpenseRepository expenseRepository;
Expense approved = expenseRepository.save(expense);

// GOOD — ApprovalService delegates to ExpenseService
private final ExpenseService expenseService;
Expense approved = expenseService.save(expense);
```
- Controllers **MUST** be thin — HTTP concerns only, delegate to services.
- Controllers **MUST** return `ResponseEntity<ApiResponse<T>>`.
- Entities **MUST NOT** be exposed through APIs — use DTOs.
- Mappers **MUST** be `@Component` classes in `com.example.demo.mapper`. Mapping logic **MUST NOT** be inline methods in services or controllers.
- When a new DTO response is introduced, a corresponding mapper **MUST** be created — not deferred or inlined.
- Constants (authorities, audit actions, resource types) **MUST** live in `com.example.demo.constants`. **MUST NOT** use inline string literals for repeated values.

## Code Style

- Java 21, Spring Boot 3.2.5, Maven.
- Constructor injection with `final` fields. Use `@RequiredArgsConstructor` for many deps.
- No field injection. No `@SneakyThrows`. No `@Data` on entities (use `@Getter/@Setter/@Builder`).
- `@Transactional` on writes, `@Transactional(readOnly = true)` on reads.
- No magic strings/numbers — externalize to constants or `@ConfigurationProperties`.
- No dead code, no commented-out code, no debugging code.
- Comments explain **why**, not **what**.

## Security

- JWT stateless auth. Method security via `@PreAuthorize` with `hasAuthority()`.
- Authority string literals inside `@PreAuthorize("hasAuthority('AUTHORITY_NAME')")` SpEL expressions are permitted, provided `AUTHORITY_NAME` matches a constant defined in `com.example.demo.constants.Authorities`.
- Never hardcode secrets. Never bypass auth. Never disable security for tests.
- Never log tokens, passwords, or PII.
- Use `SecurityAuditLogger` for security events, `AuditLogService` for business audit.
- Rate limiting via `RateLimitingService`. Account lockout enforced.
- Refresh tokens hashed (SHA-256), rotated on use, revoked on logout/password change.
- MFA supported (TOTP + EMAIL).
- All untrusted input **MUST** be validated server-side with Jakarta Bean Validation (`@Valid` on every `@RequestBody`).
- Use the existing `GlobalExceptionHandler` — never catch broad exceptions unless the layer handles them meaningfully.

## Database

- H2 for dev/test, PostgreSQL for prod.
- Flyway migrations in `src/main/resources/db/migration/` (`V{version}__{description}.sql`).
- `ddl-auto=validate` (dev/test), `none` (prod).
- Parameterized queries only — never concatenate untrusted input into SQL.
- Use `Pageable` for list endpoints. Avoid N+1 queries.

## Testing

- Unit tests: JUnit 5 + Mockito + AssertJ.
- Integration tests: `@SpringBootTest` with `@ActiveProfiles("test")` and H2.
- Security tests **MUST** cover: unauthenticated access, unauthorized access, invalid/expired tokens, validation failures, ownership violations.
- **MUST NOT** delete or weaken tests to make implementation pass.

## Agent Workflow

### 1. Understand
- Read this file. Inspect existing code, tests, and migrations. Do not immediately start coding.

### 2. Plan — Impact List
Before writing code, enumerate every file affected:
- **New files**: entities, DTOs, mappers, repositories, services, controllers, migrations, constants.
- **Modified files**: services adding deps, controllers wiring endpoints, security config for new paths.
- **Import updates**: all files referencing moved or renamed classes.
- **Test files**: new or updated test classes.

### 3. Implement
- Smallest clean solution. Follow existing conventions. Do not modify unrelated code.

### 4. Test
```bash
mvnw.cmd clean test
```

### 5. Review
Check diff for: correctness, security, missing validation, sensitive logging, duplicate logic, unnecessary deps, backward compatibility.

## Forbidden

- Disable auth/authz to make things work.
- Hardcode secrets or credentials.
- Log credentials or tokens.
- Expose stack traces or internal errors.
- Concatenate untrusted input into SQL.
- Delete tests to pass CI.
- Introduce unnecessary dependencies.
- Perform large refactors for small requests.

## Definition of Done

- Requirement correctly implemented.
- Follows project architecture.
- Input validated. Authorization enforced.
- Tests exist and pass. Build succeeds.
- No secrets exposed. No sensitive logging.
- No unrelated code changed. Diff reviewed.

If a requirement conflicts with security, correctness, or maintainability, do not silently implement an unsafe solution. Explain the conflict and propose a safe alternative.
