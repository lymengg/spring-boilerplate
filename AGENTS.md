# AGENTS.md

## Goal

Write production-quality code that is:

- Clean
- Simple
- Maintainable
- Secure
- Testable
- Following industry best practices

## Coding Rules

- Follow the existing project structure and conventions.
- Keep classes and methods small and focused.
- Follow Single Responsibility Principle.
- Use meaningful names.
- Avoid duplicated code.
- Avoid unnecessary abstractions and design patterns.
- Prefer simple and readable solutions.
- Keep business logic separate from controllers, persistence, and infrastructure.
- Do not modify unrelated code.
- Reuse existing components when appropriate.

## Security

Follow OWASP Top 10 principles.

Always:

- Validate all user input.
- Enforce authorization on the server.
- Use parameterized queries.
- Prevent SQL injection, XSS, CSRF, SSRF, path traversal, and command injection.
- Never trust client-provided roles, permissions, or IDs.
- Never hard-code passwords, API keys, tokens, or secrets.
- Never log passwords, tokens, or secrets.
- Use secure authentication and password hashing.
- Do not expose sensitive information or stack traces.
- Use least privilege.
- Keep dependencies secure and maintained.

## Testing

- Add tests for new functionality.
- Test success and failure cases.
- Test security-sensitive functionality.
- Add regression tests when fixing bugs.

## Before Finishing

Review the code for:

1. Correctness
2. Security
3. Single responsibility
4. Maintainability
5. Error handling
6. Performance
7. Tests
8. Unnecessary complexity

Prefer **simple, secure, readable code** over clever code.