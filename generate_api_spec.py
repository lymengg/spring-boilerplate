#!/usr/bin/env python3
"""Generate API Specification PDF for the Spring Boot Boilerplate project using ReportLab."""

from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER


def generate_pdf():
    doc = SimpleDocTemplate(
        "opencode_project_api_spec.pdf",
        pagesize=letter,
        rightMargin=54,
        leftMargin=54,
        topMargin=54,
        bottomMargin=54,
    )
    story = []

    styles = getSampleStyleSheet()

    title_style = ParagraphStyle(
        "DocTitle",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=24,
        leading=28,
        textColor=colors.HexColor("#1A365D"),
        spaceAfter=15,
    )

    subtitle_style = ParagraphStyle(
        "SubTitle",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=12,
        leading=16,
        textColor=colors.HexColor("#4B5563"),
        spaceAfter=20,
    )

    h1_style = ParagraphStyle(
        "Heading1",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=16,
        leading=20,
        textColor=colors.HexColor("#1A365D"),
        spaceBefore=18,
        spaceAfter=10,
        keepWithNext=True,
    )

    h2_style = ParagraphStyle(
        "Heading2",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=12,
        leading=16,
        textColor=colors.HexColor("#0D9488"),
        spaceBefore=12,
        spaceAfter=6,
        keepWithNext=True,
    )

    h3_style = ParagraphStyle(
        "Heading3",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=10,
        leading=14,
        textColor=colors.HexColor("#374151"),
        spaceBefore=8,
        spaceAfter=4,
        keepWithNext=True,
    )

    body_style = ParagraphStyle(
        "Body",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=10,
        leading=14,
        textColor=colors.HexColor("#1F2937"),
        spaceAfter=8,
    )

    code_style = ParagraphStyle(
        "CodeBlock",
        parent=styles["Normal"],
        fontName="Courier",
        fontSize=9,
        leading=12,
        textColor=colors.HexColor("#111827"),
        spaceAfter=8,
    )

    small_style = ParagraphStyle(
        "Small",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=8,
        leading=10,
        textColor=colors.HexColor("#6B7280"),
        spaceAfter=4,
    )

    method_style = ParagraphStyle(
        "Method",
        parent=styles["Normal"],
        fontName="Courier-Bold",
        fontSize=9,
        leading=12,
        spaceAfter=2,
    )

    # ── Title Page ──
    story.append(Spacer(1, 80))
    story.append(Paragraph("OpenCode Project", title_style))
    story.append(Paragraph("API Specification", title_style))
    story.append(Spacer(1, 10))
    story.append(Paragraph("Spring Boot 3.2.5 / Java 21 / Maven", subtitle_style))
    story.append(Spacer(1, 20))
    story.append(Paragraph("Generated from codebase analysis", subtitle_style))
    story.append(Spacer(1, 10))
    story.append(Paragraph("Base URL: <font face='Courier' color='#0D9488'>http://localhost:8080</font>", body_style))
    story.append(Spacer(1, 10))
    story.append(Paragraph("Total Endpoints: <b>48</b> (7 public + 41 authenticated)", body_style))
    story.append(PageBreak())

    # ── Table of Contents ──
    story.append(Paragraph("Table of Contents", h1_style))
    toc_items = [
        "1. Overview",
        "2. Technology Stack",
        "3. Authentication",
        "4. Authentication Endpoints",
        "5. Expense Endpoints",
        "6. User Management Endpoints",
        "7. Tenant Management Endpoints",
        "8. Role Management Endpoints",
        "9. Department Management Endpoints",
        "10. Audit Log Endpoints",
        "11. Data Models",
        "12. Error Handling",
        "13. Permissions Reference",
        "14. Roles Reference",
    ]
    for item in toc_items:
        story.append(Paragraph(item, body_style))
    story.append(PageBreak())

    # ── 1. Overview ──
    story.append(Paragraph("1. Overview", h1_style))
    story.append(Paragraph(
        "This document describes all REST API endpoints exposed by the Spring Boot Boilerplate application. "
        "The system implements a multi-tenant expense management platform with role-based access control (RBAC), "
        "JWT authentication, multi-factor authentication (MFA), audit logging, and rate limiting.",
        body_style,
    ))
    story.append(Paragraph(
        "<b>Architecture:</b> Controller → Service → Repository → Database",
        body_style,
    ))
    story.append(Paragraph(
        "<b>Response Format:</b> All endpoints return <font face='Courier'>ApiResponse&lt;T&gt;</font> with fields: "
        "<font face='Courier'>success</font> (boolean), <font face='Courier'>message</font> (String), "
        "<font face='Courier'>data</font> (T), <font face='Courier'>timestamp</font> (ISO-8601).",
        body_style,
    ))

    # ── 2. Technology Stack ──
    story.append(Paragraph("2. Technology Stack", h1_style))
    tech_data = [
        [Paragraph("<b>Component</b>", body_style), Paragraph("<b>Technology</b>", body_style), Paragraph("<b>Version</b>", body_style)],
        [Paragraph("Language", body_style), Paragraph("Java", body_style), Paragraph("21", body_style)],
        [Paragraph("Framework", body_style), Paragraph("Spring Boot", body_style), Paragraph("3.2.5", body_style)],
        [Paragraph("Build", body_style), Paragraph("Maven", body_style), Paragraph("Wrapper", body_style)],
        [Paragraph("ORM", body_style), Paragraph("Spring Data JPA + Hibernate", body_style), Paragraph("Via Spring Boot", body_style)],
        [Paragraph("Migrations", body_style), Paragraph("Flyway", body_style), Paragraph("16 versions", body_style)],
        [Paragraph("Auth", body_style), Paragraph("Spring Security + JJWT", body_style), Paragraph("0.12.5", body_style)],
        [Paragraph("Password", body_style), Paragraph("BCrypt", body_style), Paragraph("Strength 12", body_style)],
        [Paragraph("MFA", body_style), Paragraph("TOTP / Email", body_style), Paragraph("1.7.1", body_style)],
        [Paragraph("Cache", body_style), Paragraph("Redis", body_style), Paragraph("Via Spring Boot", body_style)],
        [Paragraph("Validation", body_style), Paragraph("Jakarta Bean Validation", body_style), Paragraph("Via Spring Boot", body_style)],
        [Paragraph("Dev DB", body_style), Paragraph("H2 (in-memory)", body_style), Paragraph("-", body_style)],
        [Paragraph("Prod DB", body_style), Paragraph("PostgreSQL", body_style), Paragraph("-", body_style)],
    ]
    tech_table = Table(tech_data, colWidths=[120, 230, 154])
    tech_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(tech_table)
    story.append(PageBreak())

    # ── 3. Authentication ──
    story.append(Paragraph("3. Authentication", h1_style))
    story.append(Paragraph(
        "The API uses <b>JWT-based stateless authentication</b>. Clients authenticate via "
        "<font face='Courier'>POST /api/auth/login</font> and receive an access token. "
        "The access token must be sent in the <font face='Courier'>Authorization: Bearer &lt;token&gt;</font> header.",
        body_style,
    ))

    story.append(Paragraph("3.1 JWT Configuration", h2_style))
    jwt_data = [
        [Paragraph("<b>Property</b>", body_style), Paragraph("<b>Value</b>", body_style)],
        [Paragraph("Algorithm", body_style), Paragraph("HMAC-SHA512", body_style)],
        [Paragraph("Access Token Expiry", body_style), Paragraph("900,000 ms (15 minutes)", body_style)],
        [Paragraph("Refresh Token Expiry", body_style), Paragraph("604,800,000 ms (7 days)", body_style)],
        [Paragraph("Issuer", body_style), Paragraph("security-boilerplate", body_style)],
        [Paragraph("Audience", body_style), Paragraph("api.security-boilerplate", body_style)],
        [Paragraph("Password Encoding", body_style), Paragraph("BCrypt (strength 12)", body_style)],
        [Paragraph("Session Policy", body_style), Paragraph("STATELESS", body_style)],
    ]
    jwt_table = Table(jwt_data, colWidths=[180, 324])
    jwt_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(jwt_table)

    story.append(Paragraph("3.2 Access Token Claims", h2_style))
    claims_data = [
        [Paragraph("<b>Claim</b>", body_style), Paragraph("<b>Type</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("sub", code_style), Paragraph("String", body_style), Paragraph("Username", body_style)],
        [Paragraph("roles", code_style), Paragraph("List&lt;String&gt;", body_style), Paragraph("Authorities (ROLE_*, EXPENSE_READ, etc.)", body_style)],
        [Paragraph("userId", code_style), Paragraph("Long", body_style), Paragraph("User database ID", body_style)],
        [Paragraph("tenantId", code_style), Paragraph("Long", body_style), Paragraph("Tenant ID (null for super admin)", body_style)],
        [Paragraph("departmentId", code_style), Paragraph("Long", body_style), Paragraph("Department ID", body_style)],
        [Paragraph("jti", code_style), Paragraph("UUID", body_style), Paragraph("Unique token ID for blacklisting", body_style)],
    ]
    claims_table = Table(claims_data, colWidths=[100, 100, 304])
    claims_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(claims_table)

    story.append(Paragraph("3.3 Authentication Flow", h2_style))
    story.append(Paragraph(
        "<b>1. Login:</b> Client sends credentials to <font face='Courier'>POST /api/auth/login</font>. "
        "If MFA is enabled, returns <font face='Courier'>MfaLoginResponse</font> with a temporary session token. "
        "Otherwise returns <font face='Courier'>TokenResponse</font> with access and refresh tokens. "
        "Refresh token is set in an httpOnly, secure, SameSite=Strict cookie.",
        body_style,
    ))
    story.append(Paragraph(
        "<b>2. MFA Verification:</b> If MFA is required, client sends the session token and 6-digit code "
        "to <font face='Courier'>POST /api/auth/mfa/verify</font>. On success, returns the full token pair.",
        body_style,
    ))
    story.append(Paragraph(
        "<b>3. Token Refresh:</b> Client sends <font face='Courier'>POST /api/auth/refresh</font> "
        "(refresh token read from cookie). Issues new access+refresh pair (rotation). Old refresh token is revoked.",
        body_style,
    ))
    story.append(Paragraph(
        "<b>4. Logout:</b> Client sends <font face='Courier'>POST /api/auth/logout</font>. "
        "Blacklists the access token JTI in Redis. Revokes refresh tokens. Clears the refresh token cookie.",
        body_style,
    ))

    story.append(Paragraph("3.4 Security Features", h2_style))
    story.append(Paragraph(
        "<b>Account Lockout:</b> 5 failed attempts → 15 min lockout. "
        "<b>Rate Limiting:</b> Redis-based sliding window (forgot-password: 10/min, reset-password: 10/min, MFA verify: 10/min). "
        "<b>Password Reset Tokens:</b> SHA-256 hashed, single-use, time-limited. "
        "<b>Refresh Tokens:</b> Stored in Redis, SHA-256 hashed, rotated on use.",
        body_style,
    ))
    story.append(PageBreak())

    # ── Helper to build endpoint table ──
    def endpoint_table(rows):
        header = [
            Paragraph("<b>Method</b>", body_style),
            Paragraph("<b>Path</b>", body_style),
            Paragraph("<b>Description</b>", body_style),
            Paragraph("<b>Auth</b>", body_style),
        ]
        data = [header]
        for r in rows:
            method_color = {
                "GET": "#059669",
                "POST": "#2563EB",
                "PUT": "#D97706",
                "DELETE": "#DC2626",
            }.get(r[0], "#374151")
            data.append([
                Paragraph(f'<font color="{method_color}"><b>{r[0]}</b></font>', method_style),
                Paragraph(f'<font face="Courier" size="8">{r[1]}</font>', body_style),
                Paragraph(r[2], body_style),
                Paragraph(r[3] if len(r) > 3 else "-", small_style),
            ])
        t = Table(data, colWidths=[55, 180, 175, 94])
        t.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
            ("ALIGN", (0, 0), (-1, -1), "LEFT"),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
        ]))
        return t

    # ── Helper for DTO tables ──
    def dto_table(title, fields):
        story.append(Paragraph(title, h3_style))
        header = [
            Paragraph("<b>Field</b>", body_style),
            Paragraph("<b>Type</b>", body_style),
            Paragraph("<b>Validation</b>", body_style),
            Paragraph("<b>Description</b>", body_style),
        ]
        data = [header]
        for f in fields:
            data.append([
                Paragraph(f[0], code_style),
                Paragraph(f[1], body_style),
                Paragraph(f[2], small_style),
                Paragraph(f[3], body_style),
            ])
        t = Table(data, colWidths=[110, 80, 130, 184])
        t.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
            ("ALIGN", (0, 0), (-1, -1), "LEFT"),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
        ]))
        story.append(t)
        story.append(Spacer(1, 6))

    # ── 4. Authentication Endpoints ──
    story.append(Paragraph("4. Authentication Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/auth</font>", body_style))
    story.append(Spacer(1, 6))

    auth_rows = [
        ("POST", "/api/auth/login", "Login with credentials", "Public"),
        ("POST", "/api/auth/mfa/verify", "Verify MFA code", "Public"),
        ("POST", "/api/auth/refresh", "Refresh access token (cookie)", "Public"),
        ("POST", "/api/auth/forgot-password", "Request password reset email", "Public"),
        ("POST", "/api/auth/reset-password", "Reset password with token", "Public"),
        ("POST", "/api/auth/logout", "Logout, revoke tokens", "Authenticated"),
        ("GET", "/api/auth/me", "Get current user profile", "Authenticated"),
        ("POST", "/api/auth/change-password", "Change password", "Authenticated"),
    ]
    story.append(endpoint_table(auth_rows))
    story.append(Spacer(1, 8))

    dto_table("LoginRequest", [
        ("usernameOrEmail", "String", "@NotBlank", "Username or email address"),
        ("password", "String", "@NotBlank, @Size(max=100)", "Password"),
        ("rememberMe", "Boolean", "-", "Optional remember-me flag"),
    ])
    dto_table("TokenResponse", [
        ("accessToken", "String", "-", "JWT access token"),
        ("refreshToken", "String", "-", "JWT refresh token (stripped in response)"),
        ("tokenType", "String", "-", "Always 'Bearer'"),
        ("expiresIn", "long", "-", "Access token expiry (ms)"),
        ("username", "String", "-", "Authenticated username"),
        ("roles", "String[]", "-", "Granted authorities"),
    ])
    dto_table("MfaLoginResponse", [
        ("mfaRequired", "boolean", "-", "Always true"),
        ("mfaSessionToken", "String", "-", "Temporary token for MFA verification"),
        ("method", "String", "-", "MFA method (TOTP, EMAIL)"),
        ("expiresIn", "long", "-", "Session token expiry (ms)"),
    ])
    dto_table("MfaVerifyRequest", [
        ("mfaSessionToken", "String", "@NotBlank", "MFA session token from login"),
        ("code", "String", "@NotBlank, @Size(min=6, max=6)", "6-digit MFA code"),
    ])
    dto_table("ChangePasswordRequest", [
        ("currentPassword", "String", "@NotBlank", "Current password"),
        ("newPassword", "String", "@NotBlank, @Password", "New password (custom validation)"),
        ("confirmPassword", "String", "@NotBlank", "Confirmation of new password"),
    ])
    dto_table("ForgotPasswordRequest", [
        ("email", "String", "@NotBlank, @Email", "Email address for reset link"),
    ])
    dto_table("ResetPasswordRequest", [
        ("token", "String", "@NotBlank", "Password reset token"),
        ("newPassword", "String", "@NotBlank, @Password", "New password"),
        ("confirmPassword", "String", "@NotBlank", "Confirmation of new password"),
    ])
    dto_table("UserProfileResponse", [
        ("username", "String", "-", "Username"),
        ("email", "String", "-", "Email address"),
        ("firstName", "String", "-", "First name"),
        ("lastName", "String", "-", "Last name"),
        ("roles", "String[]", "-", "Assigned role names"),
        ("enabled", "Boolean", "-", "Whether account is enabled"),
        ("mfaEnabled", "Boolean", "-", "Whether MFA is enabled"),
        ("mfaMethod", "String", "-", "MFA method if enabled"),
    ])
    story.append(PageBreak())

    # ── 5. Expense Endpoints ──
    story.append(Paragraph("5. Expense Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/expenses</font>", body_style))
    story.append(Spacer(1, 6))

    expense_rows = [
        ("GET", "/api/expenses", "List expenses (paginated, filtered)", "EXPENSE_READ"),
        ("GET", "/api/expenses/{id}", "Get expense by ID", "EXPENSE_READ"),
        ("POST", "/api/expenses", "Create expense", "EXPENSE_CREATE"),
        ("PUT", "/api/expenses/{id}", "Update expense", "EXPENSE_UPDATE"),
        ("POST", "/api/expenses/{id}/cancel", "Cancel expense", "EXPENSE_UPDATE"),
        ("POST", "/api/expenses/{id}/approve", "Approve expense", "EXPENSE_APPROVE"),
        ("POST", "/api/expenses/{id}/reject", "Reject expense", "EXPENSE_REJECT"),
        ("POST", "/api/expenses/{id}/process", "Process for payment", "EXPENSE_PROCESS"),
    ]
    story.append(endpoint_table(expense_rows))
    story.append(Spacer(1, 8))

    story.append(Paragraph("<b>Query Parameters (GET /api/expenses):</b>", body_style))
    query_data = [
        [Paragraph("<b>Parameter</b>", body_style), Paragraph("<b>Type</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("status", code_style), Paragraph("ExpenseStatus", body_style), Paragraph("Filter by status (PENDING, APPROVED, REJECTED, CANCELLED, PROCESSED)", body_style)],
        [Paragraph("tenantId", code_style), Paragraph("Long", body_style), Paragraph("Filter by tenant ID", body_style)],
        [Paragraph("departmentId", code_style), Paragraph("Long", body_style), Paragraph("Filter by department ID", body_style)],
        [Paragraph("page", code_style), Paragraph("Integer", body_style), Paragraph("Page number (default 0)", body_style)],
        [Paragraph("size", code_style), Paragraph("Integer", body_style), Paragraph("Page size (default 20)", body_style)],
        [Paragraph("sort", code_style), Paragraph("String", body_style), Paragraph("Sort field and direction (e.g., submissionDate,desc)", body_style)],
    ]
    qt = Table(query_data, colWidths=[110, 100, 294])
    qt.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(qt)
    story.append(Spacer(1, 8))

    dto_table("ExpenseCreateRequest", [
        ("title", "String", "@NotBlank, @Size(max=200)", "Expense title"),
        ("description", "String", "@Size(max=1000)", "Optional description"),
        ("amount", "BigDecimal", "@NotNull, @Positive, @Digits(12,4)", "Expense amount"),
        ("category", "String", "@NotBlank, @Size(max=50)", "Expense category"),
        ("departmentId", "Long", "-", "Optional department association"),
    ])
    dto_table("ExpenseUpdateRequest", [
        ("title", "String", "@NotBlank, @Size(max=200)", "Expense title"),
        ("description", "String", "@Size(max=1000)", "Optional description"),
        ("amount", "BigDecimal", "@NotNull, @Positive, @Digits(12,4)", "Expense amount"),
        ("category", "String", "@NotBlank, @Size(max=50)", "Expense category"),
    ])
    dto_table("ExpenseResponse", [
        ("id", "Long", "-", "Expense ID"),
        ("title", "String", "-", "Title"),
        ("description", "String", "-", "Description"),
        ("amount", "BigDecimal", "-", "Amount"),
        ("category", "String", "-", "Category"),
        ("status", "ExpenseStatus", "-", "PENDING, APPROVED, REJECTED, CANCELLED, PROCESSED"),
        ("submissionDate", "Instant", "-", "When submitted"),
        ("decisionDate", "Instant", "-", "When approved/rejected"),
        ("processedDate", "Instant", "-", "When processed for payment"),
        ("ownerId", "Long", "-", "Owner user ID"),
        ("ownerUsername", "String", "-", "Owner username"),
        ("departmentId", "Long", "-", "Department ID"),
        ("departmentName", "String", "-", "Department name"),
        ("tenantId", "Long", "-", "Tenant ID"),
        ("tenantName", "String", "-", "Tenant name"),
        ("approvedById", "Long", "-", "Approver user ID"),
        ("approvedByUsername", "String", "-", "Approver username"),
        ("rejectedById", "Long", "-", "Rejector user ID"),
        ("rejectedByUsername", "String", "-", "Rejector username"),
        ("processedById", "Long", "-", "Processor user ID"),
        ("processedByUsername", "String", "-", "Processor username"),
        ("updatedAt", "Instant", "-", "Last update timestamp"),
    ])
    story.append(PageBreak())

    # ── 6. User Management Endpoints ──
    story.append(Paragraph("6. User Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/users</font>", body_style))
    story.append(Spacer(1, 6))

    user_rows = [
        ("POST", "/api/management/users", "Create user", "USER_CREATE"),
        ("GET", "/api/management/users", "List users (paginated)", "USER_READ"),
        ("GET", "/api/management/users/{id}", "Get user by ID", "USER_READ"),
        ("PUT", "/api/management/users/{id}", "Update user", "USER_WRITE"),
        ("DELETE", "/api/management/users/{id}", "Delete user", "USER_DELETE"),
        ("POST", "/api/management/users/{id}/enable", "Toggle enabled state", "USER_ENABLE"),
        ("POST", "/api/management/users/{id}/roles", "Assign role to user", "USER_ASSIGN_ROLE"),
        ("DELETE", "/api/management/users/{id}/roles", "Remove role from user", "USER_ASSIGN_ROLE"),
        ("POST", "/api/management/users/{id}/mfa/enable", "Enable MFA for user", "USER_WRITE"),
        ("POST", "/api/management/users/{id}/mfa/disable", "Disable MFA for user", "USER_WRITE"),
        ("POST", "/api/management/users/{id}/mfa/reset", "Reset MFA for user", "USER_WRITE"),
    ]
    story.append(endpoint_table(user_rows))
    story.append(Spacer(1, 8))

    dto_table("UserCreateRequest", [
        ("username", "String", "@NotBlank, @Size(min=3, max=50)", "Username"),
        ("email", "String", "@NotBlank, @Email, @Size(max=100)", "Email"),
        ("password", "String", "@NotBlank, @Password", "Password (custom validation)"),
        ("firstName", "String", "@Size(max=50)", "First name"),
        ("lastName", "String", "@Size(max=50)", "Last name"),
        ("roleName", "String", "@Size(max=50)", "Optional role name to assign"),
        ("tenantId", "Long", "-", "Optional tenant ID"),
        ("departmentId", "Long", "@NotNull", "Required department ID"),
    ])
    dto_table("UserUpdateRequest", [
        ("firstName", "String", "@Size(max=50)", "First name"),
        ("lastName", "String", "@Size(max=50)", "Last name"),
        ("departmentId", "Long", "-", "Optional department ID"),
    ])
    dto_table("UserResponse", [
        ("id", "Long", "-", "User ID"),
        ("username", "String", "-", "Username"),
        ("email", "String", "-", "Email"),
        ("firstName", "String", "-", "First name"),
        ("lastName", "String", "-", "Last name"),
        ("enabled", "Boolean", "-", "Account enabled"),
        ("accountNonLocked", "Boolean", "-", "Account locked status"),
        ("departmentId", "Long", "-", "Department ID"),
        ("departmentName", "String", "-", "Department name"),
        ("roles", "Set&lt;String&gt;", "-", "Assigned role names"),
        ("permissions", "Set&lt;String&gt;", "-", "All effective permissions"),
        ("mfaEnabled", "Boolean", "-", "MFA enabled"),
        ("mfaMethod", "String", "-", "MFA method"),
        ("createdAt", "Instant", "-", "Creation timestamp"),
        ("updatedAt", "Instant", "-", "Last update timestamp"),
    ])
    dto_table("UserEnableRequest", [
        ("enabled", "Boolean", "@NotNull", "Desired enabled state"),
    ])
    dto_table("UserRoleAssignmentRequest", [
        ("roleName", "String", "@NotBlank", "Role name to assign/remove"),
    ])
    dto_table("UserMfaToggleRequest", [
        ("method", "MfaMethod", "@NotNull", "MFA method (TOTP or EMAIL)"),
    ])
    story.append(PageBreak())

    # ── 7. Tenant Management Endpoints ──
    story.append(Paragraph("7. Tenant Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/tenants</font>", body_style))
    story.append(Spacer(1, 6))

    tenant_rows = [
        ("GET", "/api/management/tenants", "List tenants (paginated, filtered)", "TENANT_READ"),
        ("GET", "/api/management/tenants/{id}", "Get tenant by ID", "TENANT_READ"),
        ("POST", "/api/management/tenants", "Create tenant", "TENANT_CREATE"),
        ("PUT", "/api/management/tenants/{id}", "Update tenant", "TENANT_UPDATE"),
        ("DELETE", "/api/management/tenants/{id}", "Delete tenant", "TENANT_DELETE"),
    ]
    story.append(endpoint_table(tenant_rows))
    story.append(Spacer(1, 8))

    dto_table("TenantCreateRequest", [
        ("name", "String", "@NotBlank, @Size(max=100)", "Tenant name"),
        ("status", "TenantStatus", "@NotNull", "ACTIVE, INACTIVE, SUSPENDED"),
    ])
    dto_table("TenantUpdateRequest", [
        ("name", "String", "@NotBlank, @Size(max=100)", "Tenant name"),
        ("status", "TenantStatus", "@NotNull", "ACTIVE, INACTIVE, SUSPENDED"),
    ])
    dto_table("TenantResponse", [
        ("id", "Long", "-", "Tenant ID"),
        ("name", "String", "-", "Tenant name"),
        ("status", "TenantStatus", "-", "ACTIVE, INACTIVE, SUSPENDED"),
        ("createdAt", "Instant", "-", "Creation timestamp"),
    ])

    # ── 8. Role Management Endpoints ──
    story.append(Paragraph("8. Role Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/roles</font>", body_style))
    story.append(Spacer(1, 6))

    role_rows = [
        ("GET", "/api/management/roles", "List roles (paginated)", "ROLE_READ"),
        ("GET", "/api/management/roles/{id}", "Get role by ID", "ROLE_READ"),
        ("POST", "/api/management/roles", "Create role", "hasRole(PLATFORM_ADMIN)"),
        ("PUT", "/api/management/roles/{id}", "Update role", "hasRole(PLATFORM_ADMIN)"),
        ("DELETE", "/api/management/roles/{id}", "Delete role", "hasRole(PLATFORM_ADMIN)"),
        ("POST", "/api/management/roles/{id}/permissions", "Add permission", "hasRole(PLATFORM_ADMIN)"),
        ("DELETE", "/api/management/roles/{id}/permissions", "Remove permission", "hasRole(PLATFORM_ADMIN)"),
    ]
    story.append(endpoint_table(role_rows))
    story.append(Spacer(1, 8))

    dto_table("RoleCreateRequest", [
        ("name", "String", "@NotBlank, @Size(max=50)", "Role name (unique key)"),
        ("title", "String", "@Size(max=100)", "Human-readable title"),
        ("description", "String", "@Size(max=255)", "Description"),
    ])
    dto_table("RolePermissionRequest", [
        ("permission", "UserPermission", "@NotNull", "Permission enum value"),
    ])
    dto_table("RoleResponse", [
        ("id", "Long", "-", "Role ID"),
        ("name", "String", "-", "Role name"),
        ("title", "String", "-", "Human-readable title"),
        ("description", "String", "-", "Description"),
        ("permissions", "Set&lt;UserPermission&gt;", "-", "Assigned permissions"),
    ])
    story.append(PageBreak())

    # ── 9. Department Management Endpoints ──
    story.append(Paragraph("9. Department Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/departments</font>", body_style))
    story.append(Spacer(1, 6))

    dept_rows = [
        ("GET", "/api/management/departments", "List departments (paginated)", "DEPARTMENT_READ"),
        ("GET", "/api/management/departments/{id}", "Get department by ID", "DEPARTMENT_READ"),
        ("POST", "/api/management/departments", "Create department", "DEPARTMENT_CREATE"),
        ("PUT", "/api/management/departments/{id}", "Update department", "DEPARTMENT_UPDATE"),
        ("DELETE", "/api/management/departments/{id}", "Delete department", "DEPARTMENT_DELETE"),
    ]
    story.append(endpoint_table(dept_rows))
    story.append(Spacer(1, 8))

    dto_table("DepartmentCreateRequest", [
        ("name", "String", "@NotBlank, @Size(max=100)", "Department name"),
        ("tenantId", "Long", "@NotNull", "Parent tenant ID"),
        ("managerIds", "List&lt;Long&gt;", "-", "Optional list of manager user IDs"),
    ])
    dto_table("DepartmentUpdateRequest", [
        ("name", "String", "@NotBlank, @Size(max=100)", "Department name"),
        ("managerIds", "List&lt;Long&gt;", "-", "Optional list of manager user IDs"),
    ])
    dto_table("DepartmentResponse", [
        ("id", "Long", "-", "Department ID"),
        ("name", "String", "-", "Department name"),
        ("tenantId", "Long", "-", "Parent tenant ID"),
        ("tenantName", "String", "-", "Parent tenant name"),
        ("managerIds", "List&lt;Long&gt;", "-", "Manager user IDs"),
        ("managerUsernames", "List&lt;String&gt;", "-", "Manager usernames"),
    ])

    # ── 10. Audit Log Endpoints ──
    story.append(Paragraph("10. Audit Log Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/audit</font>", body_style))
    story.append(Spacer(1, 6))

    audit_rows = [
        ("GET", "/api/management/audit", "List audit logs (paginated)", "AUDIT_LOG_READ"),
        ("GET", "/api/management/audit/{id}", "Get audit log by ID", "AUDIT_LOG_READ"),
    ]
    story.append(endpoint_table(audit_rows))
    story.append(Spacer(1, 8))

    dto_table("AuditLogResponse", [
        ("id", "Long", "-", "Audit log ID"),
        ("actorId", "Long", "-", "Actor user ID"),
        ("actorUsername", "String", "-", "Actor username"),
        ("tenantId", "Long", "-", "Tenant ID"),
        ("action", "String", "-", "Action performed"),
        ("resourceType", "String", "-", "Resource type (USER, EXPENSE, TENANT)"),
        ("resourceId", "String", "-", "Resource ID"),
        ("details", "String", "-", "Additional details"),
        ("timestamp", "Instant", "-", "When the action occurred"),
    ])
    story.append(PageBreak())

    # ── 11. Data Models ──
    story.append(Paragraph("11. Data Models", h1_style))

    story.append(Paragraph("11.1 ExpenseStatus Enum", h2_style))
    status_data = [
        [Paragraph("<b>Value</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("PENDING", code_style), Paragraph("Expense submitted, awaiting review", body_style)],
        [Paragraph("APPROVED", code_style), Paragraph("Expense approved by a manager", body_style)],
        [Paragraph("REJECTED", code_style), Paragraph("Expense rejected by a manager", body_style)],
        [Paragraph("CANCELLED", code_style), Paragraph("Expense cancelled by the owner", body_style)],
        [Paragraph("PROCESSED", code_style), Paragraph("Approved expense processed for payment", body_style)],
    ]
    st = Table(status_data, colWidths=[120, 384])
    st.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(st)

    story.append(Paragraph("11.2 TenantStatus Enum", h2_style))
    ts_data = [
        [Paragraph("<b>Value</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("ACTIVE", code_style), Paragraph("Tenant is active and operational", body_style)],
        [Paragraph("INACTIVE", code_style), Paragraph("Tenant is temporarily inactive", body_style)],
        [Paragraph("SUSPENDED", code_style), Paragraph("Tenant is suspended", body_style)],
    ]
    tst = Table(ts_data, colWidths=[120, 384])
    tst.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(tst)

    story.append(Paragraph("11.3 MfaMethod Enum", h2_style))
    mfa_data = [
        [Paragraph("<b>Value</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("NONE", code_style), Paragraph("No MFA configured", body_style)],
        [Paragraph("TOTP", code_style), Paragraph("Time-based One-Time Password (authenticator app)", body_style)],
        [Paragraph("EMAIL", code_style), Paragraph("Email-based MFA codes", body_style)],
    ]
    mt = Table(mfa_data, colWidths=[120, 384])
    mt.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(mt)

    story.append(Paragraph("11.4 Entity Relationships", h2_style))
    story.append(Paragraph(
        "<font face='Courier' size='8'>"
        "Tenant 1----&lt;M Department<br/>"
        "Tenant 1----&lt;M User<br/>"
        "Tenant 1----&lt;M Expense<br/>"
        "Department M&gt;----M User (via department_managers)<br/>"
        "Department 1----&lt;M Expense<br/>"
        "User M&gt;----M Role (via user_roles)<br/>"
        "Role 1----&lt;M UserPermission (via role_permissions)<br/>"
        "User 1----&lt;M Expense (as owner)<br/>"
        "User 1----&lt;M RefreshToken<br/>"
        "User 1----&lt;M PasswordResetToken<br/>"
        "</font>",
        body_style,
    ))

    story.append(Paragraph("11.5 Database Tables", h2_style))
    db_data = [
        [Paragraph("<b>Table</b>", body_style), Paragraph("<b>Description</b>", body_style), Paragraph("<b>Key Indexes</b>", body_style)],
        [Paragraph("users", code_style), Paragraph("User accounts with auth details", body_style), Paragraph("unique: username, email", body_style)],
        [Paragraph("roles", code_style), Paragraph("Role definitions", body_style), Paragraph("unique: name", body_style)],
        [Paragraph("user_roles", code_style), Paragraph("User-role junction table", body_style), Paragraph("user_id, role_id", body_style)],
        [Paragraph("role_permissions", code_style), Paragraph("Role permissions (@ElementCollection)", body_style), Paragraph("role_id", body_style)],
        [Paragraph("tenants", code_style), Paragraph("Multi-tenant organizations", body_style), Paragraph("unique: name", body_style)],
        [Paragraph("departments", code_style), Paragraph("Departments within tenants", body_style), Paragraph("unique: (tenant_id, name)", body_style)],
        [Paragraph("department_managers", code_style), Paragraph("Department-manager junction", body_style), Paragraph("department_id, user_id", body_style)],
        [Paragraph("expenses", code_style), Paragraph("Expense records", body_style), Paragraph("tenant, department, owner, status", body_style)],
        [Paragraph("audit_logs", code_style), Paragraph("Audit trail entries", body_style), Paragraph("tenant, actor, resource, timestamp", body_style)],
        [Paragraph("refresh_tokens", code_style), Paragraph("Refresh tokens (Redis-backed)", body_style), Paragraph("unique: token_hash", body_style)],
        [Paragraph("password_reset_tokens", code_style), Paragraph("Password reset tokens", body_style), Paragraph("unique: token_hash", body_style)],
    ]
    dbt = Table(db_data, colWidths=[130, 190, 184])
    dbt.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(dbt)
    story.append(PageBreak())

    # ── 12. Error Handling ──
    story.append(Paragraph("12. Error Handling", h1_style))
    story.append(Paragraph(
        "All exceptions are caught by <font face='Courier'>GlobalExceptionHandler</font> and returned "
        "as <font face='Courier'>ApiResponse</font> with appropriate HTTP status codes.",
        body_style,
    ))
    err_data = [
        [Paragraph("<b>Exception</b>", body_style), Paragraph("<b>HTTP Status</b>", body_style), Paragraph("<b>Response Message</b>", body_style)],
        [Paragraph("MethodArgumentNotValidException", small_style), Paragraph("400 Bad Request", body_style), Paragraph("Validation failed", body_style)],
        [Paragraph("ConstraintViolationException", small_style), Paragraph("400 Bad Request", body_style), Paragraph("Validation failed", body_style)],
        [Paragraph("BadCredentialsException", small_style), Paragraph("401 Unauthorized", body_style), Paragraph("Invalid credentials", body_style)],
        [Paragraph("UsernameNotFoundException", small_style), Paragraph("401 Unauthorized", body_style), Paragraph("Invalid credentials", body_style)],
        [Paragraph("LockedException", small_style), Paragraph("429 Too Many Requests", body_style), Paragraph("(lockout message)", body_style)],
        [Paragraph("AccessDeniedException", small_style), Paragraph("403 Forbidden", body_style), Paragraph("Access denied: Insufficient permissions", body_style)],
        [Paragraph("IllegalArgumentException", small_style), Paragraph("400 Bad Request", body_style), Paragraph("(contextual message)", body_style)],
        [Paragraph("IllegalStateException", small_style), Paragraph("409 Conflict", body_style), Paragraph("(contextual message)", body_style)],
        [Paragraph("DataIntegrityViolationException", small_style), Paragraph("400 Bad Request", body_style), Paragraph("A record with the same value already exists", body_style)],
        [Paragraph("Exception (catch-all)", small_style), Paragraph("500 Internal Server Error", body_style), Paragraph("An unexpected error occurred", body_style)],
    ]
    errt = Table(err_data, colWidths=[180, 130, 194])
    errt.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(errt)

    # ── 13. Permissions Reference ──
    story.append(Paragraph("13. Permissions Reference", h1_style))
    story.append(Paragraph(
        "All permissions are defined in the <font face='Courier'>UserPermission</font> enum "
        "and used in <font face='Courier'>@PreAuthorize</font> annotations via "
        "<font face='Courier'>hasAuthority()</font>.",
        body_style,
    ))
    perm_data = [
        [Paragraph("<b>Category</b>", body_style), Paragraph("<b>Permissions</b>", body_style)],
        [Paragraph("Tenant Management", body_style), Paragraph("TENANT_READ, TENANT_CREATE, TENANT_UPDATE, TENANT_DELETE", body_style)],
        [Paragraph("User Management", body_style), Paragraph("USER_READ, USER_WRITE, USER_CREATE, USER_UPDATE, USER_DELETE, USER_ENABLE, USER_ASSIGN_ROLE", body_style)],
        [Paragraph("Role Management", body_style), Paragraph("ROLE_READ, ROLE_WRITE, ROLE_DELETE, ROLE_ASSIGN_PERMISSION", body_style)],
        [Paragraph("Department Management", body_style), Paragraph("DEPARTMENT_READ, DEPARTMENT_CREATE, DEPARTMENT_UPDATE, DEPARTMENT_DELETE", body_style)],
        [Paragraph("Expense Management", body_style), Paragraph("EXPENSE_READ, EXPENSE_READ_ALL, EXPENSE_CREATE, EXPENSE_UPDATE, EXPENSE_DELETE, EXPENSE_APPROVE, EXPENSE_REJECT, EXPENSE_PROCESS", body_style)],
        [Paragraph("MFA Management", body_style), Paragraph("MFA_MANAGE", body_style)],
        [Paragraph("Reporting &amp; Audit", body_style), Paragraph("REPORT_READ, AUDIT_LOG_READ", body_style)],
    ]
    pt = Table(perm_data, colWidths=[140, 364])
    pt.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(pt)

    # ── 14. Roles Reference ──
    story.append(Paragraph("14. Roles Reference", h1_style))
    story.append(Paragraph(
        "Roles are seeded in Flyway migrations. Each role grants a specific set of permissions.",
        body_style,
    ))
    role_ref_data = [
        [Paragraph("<b>Role</b>", body_style), Paragraph("<b>Description</b>", body_style), Paragraph("<b>Key Permissions</b>", body_style)],
        [Paragraph("PLATFORM_ADMIN", code_style), Paragraph("Super admin (no tenant)", body_style), Paragraph("All permissions", body_style)],
        [Paragraph("TENANT_ADMIN", code_style), Paragraph("Tenant administrator", body_style), Paragraph("All except TENANT_CREATE, TENANT_DELETE", body_style)],
        [Paragraph("USER_MANAGER", code_style), Paragraph("Manages users", body_style), Paragraph("USER_READ, USER_WRITE, USER_CREATE, USER_ASSIGN_ROLE", body_style)],
        [Paragraph("DEPARTMENT_MANAGER", code_style), Paragraph("Department manager", body_style), Paragraph("USER_READ, DEPARTMENT_READ, EXPENSE_READ, EXPENSE_APPROVE, EXPENSE_REJECT, REPORT_READ", body_style)],
        [Paragraph("EMPLOYEE", code_style), Paragraph("Regular employee", body_style), Paragraph("EXPENSE_READ, EXPENSE_CREATE, EXPENSE_UPDATE, EXPENSE_DELETE", body_style)],
        [Paragraph("AUDITOR", code_style), Paragraph("Read-only auditor", body_style), Paragraph("USER_READ, DEPARTMENT_READ, EXPENSE_READ, REPORT_READ, AUDIT_LOG_READ, EXPENSE_READ_ALL", body_style)],
        [Paragraph("FINANCE", code_style), Paragraph("Finance processor", body_style), Paragraph("EXPENSE_READ, EXPENSE_PROCESS, REPORT_READ, EXPENSE_READ_ALL", body_style)],
    ]
    rrt = Table(role_ref_data, colWidths=[120, 140, 244])
    rrt.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#F3F4F6")),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#E5E7EB")),
    ]))
    story.append(rrt)

    doc.build(story)
    print("PDF generated: opencode_project_api_spec.pdf")


if __name__ == "__main__":
    generate_pdf()
