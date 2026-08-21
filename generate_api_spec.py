#!/usr/bin/env python3
"""Generate API Specification PDF for the Expense Management System using ReportLab."""

from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
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

    BLACK = colors.HexColor("#000000")
    DARK_GRAY = colors.HexColor("#1F2937")
    MEDIUM_GRAY = colors.HexColor("#374151")
    LIGHT_GRAY = colors.HexColor("#6B7280")
    HEADER_BG = colors.HexColor("#F3F4F6")
    BORDER_COLOR = colors.HexColor("#D1D5DB")

    title_style = ParagraphStyle(
        "DocTitle", parent=styles["Normal"],
        fontName="Helvetica-Bold", fontSize=24, leading=28,
        textColor=BLACK, spaceAfter=8,
    )
    subtitle_style = ParagraphStyle(
        "SubTitle", parent=styles["Normal"],
        fontName="Helvetica", fontSize=11, leading=15,
        textColor=LIGHT_GRAY, spaceAfter=12,
    )
    h1_style = ParagraphStyle(
        "Heading1", parent=styles["Normal"],
        fontName="Helvetica-Bold", fontSize=14, leading=18,
        textColor=BLACK, spaceBefore=16, spaceAfter=8,
        keepWithNext=True,
    )
    h2_style = ParagraphStyle(
        "Heading2", parent=styles["Normal"],
        fontName="Helvetica-Bold", fontSize=11, leading=15,
        textColor=MEDIUM_GRAY, spaceBefore=10, spaceAfter=6,
        keepWithNext=True,
    )
    h3_style = ParagraphStyle(
        "Heading3", parent=styles["Normal"],
        fontName="Helvetica-Bold", fontSize=10, leading=13,
        textColor=DARK_GRAY, spaceBefore=8, spaceAfter=4,
        keepWithNext=True,
    )
    body_style = ParagraphStyle(
        "Body", parent=styles["Normal"],
        fontName="Helvetica", fontSize=10, leading=14,
        textColor=DARK_GRAY, spaceAfter=6,
    )
    code_style = ParagraphStyle(
        "CodeBlock", parent=styles["Normal"],
        fontName="Courier", fontSize=9, leading=12,
        textColor=DARK_GRAY, spaceAfter=6,
    )
    small_style = ParagraphStyle(
        "Small", parent=styles["Normal"],
        fontName="Helvetica", fontSize=8, leading=10,
        textColor=LIGHT_GRAY, spaceAfter=4,
    )
    method_style = ParagraphStyle(
        "Method", parent=styles["Normal"],
        fontName="Courier-Bold", fontSize=9, leading=12,
        spaceAfter=2,
    )

    def styled_table(data, col_widths):
        t = Table(data, colWidths=col_widths)
        t.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), HEADER_BG),
            ("ALIGN", (0, 0), (-1, -1), "LEFT"),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("LEFTPADDING", (0, 0), (-1, -1), 6),
            ("RIGHTPADDING", (0, 0), (-1, -1), 6),
            ("LINEBELOW", (0, 0), (-1, 0), 0.75, BORDER_COLOR),
            ("LINEBELOW", (0, 1), (-1, -1), 0.5, BORDER_COLOR),
        ]))
        return t

    def endpoint_table(rows):
        header = [
            Paragraph("<b>Method</b>", body_style),
            Paragraph("<b>Path</b>", body_style),
            Paragraph("<b>Description</b>", body_style),
            Paragraph("<b>Auth</b>", body_style),
        ]
        data = [header]
        for r in rows:
            data.append([
                Paragraph(f'<b>{r[0]}</b>', method_style),
                Paragraph(f'<font face="Courier" size="8">{r[1]}</font>', body_style),
                Paragraph(r[2], body_style),
                Paragraph(r[3] if len(r) > 3 else "-", small_style),
            ])
        return styled_table(data, [55, 180, 175, 94])

    def dto_table(title, fields):
        story.append(Paragraph(title, h3_style))
        header = [
            Paragraph("<b>Field</b>", body_style),
            Paragraph("<b>Type</b>", body_style),
            Paragraph("<b>Constraints</b>", body_style),
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
        story.append(styled_table(data, [110, 80, 130, 184]))
        story.append(Spacer(1, 6))

    def status_table(rows):
        header = [
            Paragraph("<b>Code</b>", body_style),
            Paragraph("<b>Description</b>", body_style),
        ]
        data = [header]
        for r in rows:
            data.append([
                Paragraph(f"<b>{r[0]}</b>", code_style),
                Paragraph(r[1], body_style),
            ])
        story.append(styled_table(data, [80, 424]))
        story.append(Spacer(1, 6))

    # ══════════════════════════════════════════════════════════════
    #  TITLE PAGE
    # ══════════════════════════════════════════════════════════════
    story.append(Spacer(1, 100))
    story.append(Paragraph("Expense Management API", title_style))
    story.append(Paragraph("API Specification", subtitle_style))
    story.append(Spacer(1, 30))
    story.append(Paragraph("Version 1.0", subtitle_style))
    story.append(Paragraph("Base URL: <font face='Courier'>http://localhost:8080</font>", body_style))
    story.append(Paragraph("Protocol: HTTPS (production) / HTTP (development)", body_style))
    story.append(Paragraph("Authentication: JWT Bearer Token", body_style))
    story.append(Paragraph("Content-Type: application/json", body_style))
    story.append(Spacer(1, 30))
    story.append(Paragraph("Total Endpoints: 48 (7 public + 41 authenticated)", body_style))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  TABLE OF CONTENTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("Table of Contents", h1_style))
    for item in [
        "1. Overview", "2. Authentication", "3. Error Handling",
        "4. Authentication Endpoints", "5. Expense Endpoints",
        "6. User Management Endpoints", "7. Tenant Management Endpoints",
        "8. Role Management Endpoints", "9. Department Management Endpoints",
        "10. Audit Log Endpoints", "11. Enumerations",
    ]:
        story.append(Paragraph(item, body_style))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  1. OVERVIEW
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("1. Overview", h1_style))
    story.append(Paragraph(
        "This document specifies the REST API endpoints for the Expense Management System. "
        "The API follows REST conventions with JSON request/response bodies.",
        body_style,
    ))
    story.append(Paragraph("1.1 Base URL", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Environment</b>", body_style), Paragraph("<b>Base URL</b>", body_style)],
        [Paragraph("Development", body_style), Paragraph("<font face='Courier'>http://localhost:8080</font>", body_style)],
        [Paragraph("Production", body_style), Paragraph("<font face='Courier'>https://api.example.com</font>", body_style)],
    ], [180, 324]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("1.2 Response Envelope", h2_style))
    story.append(Paragraph("All endpoints return responses wrapped in the following envelope:", body_style))
    story.append(styled_table([
        [Paragraph("<b>Field</b>", body_style), Paragraph("<b>Type</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("success", code_style), Paragraph("boolean", body_style), Paragraph("true if the request succeeded", body_style)],
        [Paragraph("message", code_style), Paragraph("string", body_style), Paragraph("Human-readable status message", body_style)],
        [Paragraph("data", code_style), Paragraph("T | null", body_style), Paragraph("Response payload (null on errors)", body_style)],
        [Paragraph("timestamp", code_style), Paragraph("string", body_style), Paragraph("ISO-8601 timestamp", body_style)],
    ], [100, 80, 324]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("1.3 Pagination", h2_style))
    story.append(Paragraph(
        "List endpoints accept <font face='Courier'>page</font>, <font face='Courier'>size</font>, "
        "and <font face='Courier'>sort</font> query parameters. Responses include:",
        body_style,
    ))
    story.append(styled_table([
        [Paragraph("<b>Field</b>", body_style), Paragraph("<b>Type</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("content", code_style), Paragraph("array", body_style), Paragraph("Array of results", body_style)],
        [Paragraph("totalElements", code_style), Paragraph("integer", body_style), Paragraph("Total matching records", body_style)],
        [Paragraph("totalPages", code_style), Paragraph("integer", body_style), Paragraph("Total number of pages", body_style)],
        [Paragraph("number", code_style), Paragraph("integer", body_style), Paragraph("Current page (0-indexed)", body_style)],
        [Paragraph("size", code_style), Paragraph("integer", body_style), Paragraph("Page size", body_style)],
    ], [110, 80, 314]))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  2. AUTHENTICATION
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("2. Authentication", h1_style))
    story.append(Paragraph(
        "The API uses <b>JWT-based stateless authentication</b>. Obtain a token via "
        "<font face='Courier'>POST /api/auth/login</font> and include it in the "
        "<font face='Courier'>Authorization: Bearer &lt;token&gt;</font> header.",
        body_style,
    ))
    story.append(Paragraph("2.1 Authentication Flow", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Step</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("1. Login", body_style), Paragraph("POST /api/auth/login with credentials. Returns access + refresh tokens (or MFA challenge).", body_style)],
        [Paragraph("2. MFA (if required)", body_style), Paragraph("POST /api/auth/mfa/verify with session token + 6-digit code. Returns full token pair.", body_style)],
        [Paragraph("3. Use API", body_style), Paragraph("Include Access Token in Authorization header. Token expires in 15 minutes.", body_style)],
        [Paragraph("4. Refresh", body_style), Paragraph("POST /api/auth/refresh (cookie-based). Returns new token pair (rotation).", body_style)],
    ], [100, 404]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("2.2 Token Properties", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Property</b>", body_style), Paragraph("<b>Value</b>", body_style)],
        [Paragraph("Algorithm", body_style), Paragraph("HMAC-SHA512", body_style)],
        [Paragraph("Access Token Expiry", body_style), Paragraph("15 minutes", body_style)],
        [Paragraph("Refresh Token Expiry", body_style), Paragraph("7 days", body_style)],
        [Paragraph("Refresh Token Delivery", body_style), Paragraph("httpOnly, secure, SameSite=Strict cookie", body_style)],
    ], [180, 324]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("2.3 Authorization", h2_style))
    story.append(Paragraph(
        "Access is controlled via method-level security annotations that check authority strings. "
        "Authority strings match the permission enum values. "
        "Multi-tenant scoping is enforced at the service layer.",
        body_style,
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  3. ERROR HANDLING
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("3. Error Handling", h1_style))
    story.append(Paragraph(
        "Errors are returned as <font face='Courier'>ApiResponse</font> with "
        "<font face='Courier'>success=false</font> and a descriptive <font face='Courier'>message</font>.",
        body_style,
    ))
    story.append(Paragraph("3.1 HTTP Status Codes", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Status</b>", body_style), Paragraph("<b>Meaning</b>", body_style), Paragraph("<b>When Returned</b>", body_style)],
        [Paragraph("200 OK", body_style), Paragraph("Success", body_style), Paragraph("Request processed successfully", body_style)],
        [Paragraph("400 Bad Request", body_style), Paragraph("Validation Error", body_style), Paragraph("Invalid input, constraint violation, or business rule violation", body_style)],
        [Paragraph("401 Unauthorized", body_style), Paragraph("Authentication Failed", body_style), Paragraph("Invalid/missing credentials or token", body_style)],
        [Paragraph("403 Forbidden", body_style), Paragraph("Authorization Failed", body_style), Paragraph("Authenticated but lacks required permission", body_style)],
        [Paragraph("404 Not Found", body_style), Paragraph("Resource Not Found", body_style), Paragraph("Requested resource does not exist", body_style)],
        [Paragraph("409 Conflict", body_style), Paragraph("State Conflict", body_style), Paragraph("Invalid state transition (e.g., approving a rejected expense)", body_style)],
        [Paragraph("429 Too Many Requests", body_style), Paragraph("Rate Limited", body_style), Paragraph("Account locked or rate limit exceeded", body_style)],
        [Paragraph("500 Internal Server Error", body_style), Paragraph("Unexpected Error", body_style), Paragraph("Unhandled exception", body_style)],
    ], [110, 100, 294]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("3.2 Common Error Messages", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Error Type</b>", body_style), Paragraph("<b>Status</b>", body_style), Paragraph("<b>Message</b>", body_style)],
        [Paragraph("Validation failed", body_style), Paragraph("400", body_style), Paragraph("One or more fields failed validation", body_style)],
        [Paragraph("Invalid credentials", body_style), Paragraph("401", body_style), Paragraph("Username or password is incorrect", body_style)],
        [Paragraph("Account locked", body_style), Paragraph("429", body_style), Paragraph("Account locked due to too many failed attempts", body_style)],
        [Paragraph("Access denied", body_style), Paragraph("403", body_style), Paragraph("Insufficient permissions to perform this action", body_style)],
        [Paragraph("Resource not found", body_style), Paragraph("400", body_style), Paragraph("The requested resource does not exist", body_style)],
        [Paragraph("Invalid state transition", body_style), Paragraph("409", body_style), Paragraph("Cannot perform this action in the current state", body_style)],
        [Paragraph("Duplicate value", body_style), Paragraph("400", body_style), Paragraph("A record with the same value already exists", body_style)],
    ], [180, 50, 274]))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  4. AUTHENTICATION ENDPOINTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("4. Authentication Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/auth</font>", body_style))
    story.append(Spacer(1, 6))
    story.append(endpoint_table([
        ("POST", "/api/auth/login", "Login with credentials", "Public"),
        ("POST", "/api/auth/mfa/verify", "Verify MFA code", "Public"),
        ("POST", "/api/auth/refresh", "Refresh access token (cookie)", "Public"),
        ("POST", "/api/auth/forgot-password", "Request password reset email", "Public"),
        ("POST", "/api/auth/reset-password", "Reset password with token", "Public"),
        ("POST", "/api/auth/logout", "Logout, revoke tokens", "Authenticated"),
        ("GET", "/api/auth/me", "Get current user profile", "Authenticated"),
        ("POST", "/api/auth/change-password", "Change password", "Authenticated"),
    ]))
    story.append(Spacer(1, 8))

    # POST /api/auth/login
    story.append(Paragraph("POST /api/auth/login", h2_style))
    story.append(Paragraph("<b>Login with credentials</b>", body_style))
    dto_table("Request Body", [
        ("usernameOrEmail", "string", "Required", "Username or email address"),
        ("password", "string", "Required, max 100 characters", "Password"),
        ("rememberMe", "boolean", "Optional", "Remember-me flag"),
    ])
    story.append(Paragraph("<b>Response 200 — Success:</b>", h3_style))
    dto_table("TokenResponse", [
        ("accessToken", "string", "\u2014", "JWT access token"),
        ("refreshToken", "string", "\u2014", "JWT refresh token (stripped in response)"),
        ("tokenType", "string", "\u2014", "Always 'Bearer'"),
        ("expiresIn", "integer", "\u2014", "Access token expiry in milliseconds"),
        ("username", "string", "\u2014", "Authenticated username"),
        ("roles", "array of strings", "\u2014", "Granted authorities"),
    ])
    story.append(Paragraph("<b>Response 200 — MFA Required:</b>", h3_style))
    dto_table("MfaLoginResponse", [
        ("mfaRequired", "boolean", "\u2014", "Always true"),
        ("mfaSessionToken", "string", "\u2014", "Temporary token for MFA verification"),
        ("method", "string", "\u2014", "MFA method (TOTP, EMAIL)"),
        ("expiresIn", "integer", "\u2014", "Session token expiry in milliseconds"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Login successful (tokens returned or MFA challenge)"),
        ("400", "Validation failed"),
        ("401", "Invalid credentials"),
        ("429", "Account locked due to too many failed attempts"),
    ])

    # POST /api/auth/mfa/verify
    story.append(Paragraph("POST /api/auth/mfa/verify", h2_style))
    story.append(Paragraph("<b>Verify MFA code during login</b>", body_style))
    dto_table("Request Body", [
        ("mfaSessionToken", "string", "Required", "MFA session token from login response"),
        ("code", "string", "Required, exactly 6 characters", "6-digit MFA code"),
    ])
    story.append(Paragraph("<b>Response 200 — Success:</b>", h3_style))
    dto_table("TokenResponse", [
        ("accessToken", "string", "\u2014", "JWT access token"),
        ("refreshToken", "string", "\u2014", "JWT refresh token"),
        ("tokenType", "string", "\u2014", "Always 'Bearer'"),
        ("expiresIn", "integer", "\u2014", "Access token expiry in milliseconds"),
        ("username", "string", "\u2014", "Authenticated username"),
        ("roles", "array of strings", "\u2014", "Granted authorities"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "MFA verification successful"),
        ("400", "Invalid or expired MFA session token"),
        ("401", "Invalid MFA code"),
    ])

    # POST /api/auth/refresh
    story.append(Paragraph("POST /api/auth/refresh", h2_style))
    story.append(Paragraph("<b>Refresh access token</b>", body_style))
    story.append(Paragraph(
        "Reads refresh token from httpOnly cookie. Returns new access + refresh token pair (rotation). "
        "Old refresh token is revoked.",
        body_style,
    ))
    story.append(Paragraph("<b>Response 200 — Success:</b>", h3_style))
    dto_table("TokenResponse", [
        ("accessToken", "string", "\u2014", "JWT access token"),
        ("refreshToken", "string", "\u2014", "JWT refresh token (stripped in response)"),
        ("tokenType", "string", "\u2014", "Always 'Bearer'"),
        ("expiresIn", "integer", "\u2014", "Access token expiry in milliseconds"),
        ("username", "string", "\u2014", "Authenticated username"),
        ("roles", "array of strings", "\u2014", "Granted authorities"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Token refreshed successfully"),
        ("401", "Invalid or expired refresh token"),
    ])

    # Forgot password
    story.append(Paragraph("POST /api/auth/forgot-password", h2_style))
    story.append(Paragraph("<b>Request password reset email</b>", body_style))
    dto_table("Request Body", [
        ("email", "string", "Required, valid email format", "Email address for reset link"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Reset email sent (always returns success for security)"),
        ("400", "Validation failed"),
    ])

    # Reset password
    story.append(Paragraph("POST /api/auth/reset-password", h2_style))
    story.append(Paragraph("<b>Reset password with token</b>", body_style))
    dto_table("Request Body", [
        ("token", "string", "Required", "Password reset token from email"),
        ("newPassword", "string", "Required, custom password rules", "New password"),
        ("confirmPassword", "string", "Required", "Confirmation of new password"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Password reset successful"),
        ("400", "Invalid/expired token, passwords do not match, validation failed"),
    ])

    # Logout
    story.append(Paragraph("POST /api/auth/logout", h2_style))
    story.append(Paragraph("<b>Logout, revoke tokens</b>", body_style))
    story.append(Paragraph("Blacklists the access token JTI in Redis. Revokes refresh tokens. Clears the refresh token cookie.", body_style))
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([("200", "Logout successful")])

    # GET /api/auth/me
    story.append(Paragraph("GET /api/auth/me", h2_style))
    story.append(Paragraph("<b>Get current user profile</b>", body_style))
    story.append(Paragraph("<b>Response 200 — Success:</b>", h3_style))
    dto_table("UserProfileResponse", [
        ("username", "string", "\u2014", "Username"),
        ("email", "string", "\u2014", "Email address"),
        ("firstName", "string", "\u2014", "First name"),
        ("lastName", "string", "\u2014", "Last name"),
        ("roles", "array of strings", "\u2014", "Assigned role names"),
        ("enabled", "boolean", "\u2014", "Whether account is enabled"),
        ("mfaEnabled", "boolean", "\u2014", "Whether MFA is enabled"),
        ("mfaMethod", "string", "\u2014", "MFA method if enabled"),
    ])

    # Change password
    story.append(Paragraph("POST /api/auth/change-password", h2_style))
    story.append(Paragraph("<b>Change password</b>", body_style))
    dto_table("Request Body", [
        ("currentPassword", "string", "Required", "Current password"),
        ("newPassword", "string", "Required, custom password rules", "New password"),
        ("confirmPassword", "string", "Required", "Confirmation of new password"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Password changed successfully"),
        ("400", "Current password incorrect, validation failed"),
    ])
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  5. EXPENSE ENDPOINTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("5. Expense Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/expenses</font>", body_style))
    story.append(Spacer(1, 6))
    story.append(endpoint_table([
        ("GET", "/api/expenses", "List expenses (paginated, filtered)", "EXPENSE_READ"),
        ("GET", "/api/expenses/{id}", "Get expense by ID", "EXPENSE_READ"),
        ("POST", "/api/expenses", "Create expense", "EXPENSE_CREATE"),
        ("PUT", "/api/expenses/{id}", "Update expense", "EXPENSE_UPDATE"),
        ("POST", "/api/expenses/{id}/cancel", "Cancel expense", "EXPENSE_UPDATE"),
        ("POST", "/api/expenses/{id}/approve", "Approve expense", "EXPENSE_APPROVE"),
        ("POST", "/api/expenses/{id}/reject", "Reject expense", "EXPENSE_REJECT"),
        ("POST", "/api/expenses/{id}/process", "Process for payment", "EXPENSE_PROCESS"),
    ]))
    story.append(Spacer(1, 8))

    # GET /api/expenses
    story.append(Paragraph("GET /api/expenses", h2_style))
    story.append(Paragraph("<b>List expenses</b> \u2014 Scope depends on the user's role.", body_style))
    story.append(Paragraph("<b>Query Parameters:</b>", h3_style))
    story.append(styled_table([
        [Paragraph("<b>Parameter</b>", body_style), Paragraph("<b>Type</b>", body_style), Paragraph("<b>Required</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("status", code_style), Paragraph("string (enum)", body_style), Paragraph("No", body_style), Paragraph("Filter by status", body_style)],
        [Paragraph("tenantId", code_style), Paragraph("integer", body_style), Paragraph("No", body_style), Paragraph("Filter by tenant ID", body_style)],
        [Paragraph("departmentId", code_style), Paragraph("integer", body_style), Paragraph("No", body_style), Paragraph("Filter by department ID", body_style)],
        [Paragraph("page", code_style), Paragraph("integer", body_style), Paragraph("No", body_style), Paragraph("Page number (default 0)", body_style)],
        [Paragraph("size", code_style), Paragraph("integer", body_style), Paragraph("No", body_style), Paragraph("Page size (default 20)", body_style)],
        [Paragraph("sort", code_style), Paragraph("string", body_style), Paragraph("No", body_style), Paragraph("Sort field,direction (e.g. submissionDate,desc)", body_style)],
    ], [100, 100, 60, 244]))
    story.append(Spacer(1, 6))
    story.append(Paragraph("<b>Response 200 — Success:</b>", h3_style))
    story.append(Paragraph("Returns a paginated list of expense objects.", body_style))
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Expenses listed successfully"),
        ("400", "Invalid filter parameters"),
    ])

    # POST /api/expenses
    story.append(Paragraph("POST /api/expenses", h2_style))
    story.append(Paragraph("<b>Create expense</b>", body_style))
    dto_table("Request Body", [
        ("title", "string", "Required, max 200 characters", "Expense title"),
        ("description", "string", "Optional, max 1000 characters", "Optional description"),
        ("amount", "decimal", "Required, positive, up to 12 digits with 4 decimal places", "Expense amount"),
        ("category", "string", "Required, max 50 characters", "Expense category"),
        ("departmentId", "integer", "Optional", "Optional department association"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Expense created successfully"),
        ("400", "Validation failed"),
    ])

    # ExpenseResponse
    story.append(Paragraph("ExpenseResponse (used by all expense endpoints):", h3_style))
    dto_table("ExpenseResponse", [
        ("id", "integer", "\u2014", "Expense ID"),
        ("title", "string", "\u2014", "Title"),
        ("description", "string", "\u2014", "Description"),
        ("amount", "decimal", "\u2014", "Amount"),
        ("category", "string", "\u2014", "Category"),
        ("status", "string (enum)", "\u2014", "PENDING | APPROVED | REJECTED | CANCELLED | PROCESSED"),
        ("submissionDate", "timestamp", "\u2014", "When submitted"),
        ("decisionDate", "timestamp", "\u2014", "When approved/rejected"),
        ("processedDate", "timestamp", "\u2014", "When processed for payment"),
        ("ownerId", "integer", "\u2014", "Owner user ID"),
        ("ownerUsername", "string", "\u2014", "Owner username"),
        ("departmentId", "integer", "\u2014", "Department ID"),
        ("departmentName", "string", "\u2014", "Department name"),
        ("tenantId", "integer", "\u2014", "Tenant ID"),
        ("tenantName", "string", "\u2014", "Tenant name"),
        ("approvedById", "integer", "\u2014", "Approver user ID"),
        ("approvedByUsername", "string", "\u2014", "Approver username"),
        ("rejectedById", "integer", "\u2014", "Rejector user ID"),
        ("rejectedByUsername", "string", "\u2014", "Rejector username"),
        ("processedById", "integer", "\u2014", "Processor user ID"),
        ("processedByUsername", "string", "\u2014", "Processor username"),
        ("updatedAt", "timestamp", "\u2014", "Last update timestamp"),
    ])

    # PUT /api/expenses/{id}
    story.append(Paragraph("PUT /api/expenses/{id}", h2_style))
    story.append(Paragraph("<b>Update expense</b> \u2014 Only the owner can update a PENDING expense.", body_style))
    dto_table("Request Body", [
        ("title", "string", "Required, max 200 characters", "Expense title"),
        ("description", "string", "Optional, max 1000 characters", "Optional description"),
        ("amount", "decimal", "Required, positive, up to 12 digits with 4 decimal places", "Expense amount"),
        ("category", "string", "Required, max 50 characters", "Expense category"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Expense updated successfully"),
        ("400", "Validation failed or expense not in PENDING status"),
        ("403", "Not the expense owner"),
    ])

    # Action endpoints
    for path, purpose, codes in [
        ("POST /api/expenses/{id}/cancel", "Owner cancels a PENDING expense", [("200", "Success"), ("400", "Not in PENDING status"), ("403", "Not the owner")]),
        ("POST /api/expenses/{id}/approve", "Manager approves a PENDING expense", [("200", "Success"), ("400", "Not in PENDING status"), ("403", "Not a manager")]),
        ("POST /api/expenses/{id}/reject", "Manager rejects a PENDING expense", [("200", "Success"), ("400", "Not in PENDING status"), ("403", "Not a manager")]),
        ("POST /api/expenses/{id}/process", "Finance processes an APPROVED expense", [("200", "Success"), ("400", "Not in APPROVED status"), ("403", "Not finance")]),
    ]:
        story.append(Paragraph(path, h2_style))
        story.append(Paragraph(f"<b>{purpose}</b>", body_style))
        story.append(Paragraph("<b>Status Codes:</b>", h3_style))
        status_table(codes)

    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  6. USER MANAGEMENT ENDPOINTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("6. User Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/users</font>", body_style))
    story.append(Spacer(1, 6))
    story.append(endpoint_table([
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
    ]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("UserResponse (used by all user endpoints):", h3_style))
    dto_table("UserResponse", [
        ("id", "integer", "\u2014", "User ID"),
        ("username", "string", "\u2014", "Username"),
        ("email", "string", "\u2014", "Email"),
        ("firstName", "string", "\u2014", "First name"),
        ("lastName", "string", "\u2014", "Last name"),
        ("enabled", "boolean", "\u2014", "Account enabled"),
        ("accountNonLocked", "boolean", "\u2014", "Account locked status"),
        ("departmentId", "integer", "\u2014", "Department ID"),
        ("departmentName", "string", "\u2014", "Department name"),
        ("roles", "array of strings", "\u2014", "Assigned role names"),
        ("permissions", "array of strings", "\u2014", "All effective permissions"),
        ("mfaEnabled", "boolean", "\u2014", "MFA enabled"),
        ("mfaMethod", "string", "\u2014", "MFA method"),
        ("createdAt", "timestamp", "\u2014", "Creation timestamp"),
        ("updatedAt", "timestamp", "\u2014", "Last update timestamp"),
    ])

    # POST /api/management/users
    story.append(Paragraph("POST /api/management/users", h2_style))
    story.append(Paragraph("<b>Create a new user</b>", body_style))
    dto_table("Request Body", [
        ("username", "string", "Required, 3\u201350 characters", "Username"),
        ("email", "string", "Required, valid email, max 100 characters", "Email"),
        ("password", "string", "Required, custom password rules", "Password"),
        ("firstName", "string", "Optional, max 50 characters", "First name"),
        ("lastName", "string", "Optional, max 50 characters", "Last name"),
        ("roleName", "string", "Optional, max 50 characters", "Optional role name to assign"),
        ("tenantId", "integer", "Optional", "Optional tenant ID"),
        ("departmentId", "integer", "Required", "Required department ID"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "User created successfully"),
        ("400", "Username/email already exists, validation failed"),
    ])

    # PUT /api/management/users/{id}
    story.append(Paragraph("PUT /api/management/users/{id}", h2_style))
    story.append(Paragraph("<b>Update an existing user</b>", body_style))
    dto_table("Request Body", [
        ("firstName", "string", "Optional, max 50 characters", "First name"),
        ("lastName", "string", "Optional, max 50 characters", "Last name"),
        ("departmentId", "integer", "Optional", "Optional department ID"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "User updated successfully"),
        ("400", "Validation failed"),
    ])

    # DELETE /api/management/users/{id}
    story.append(Paragraph("DELETE /api/management/users/{id}", h2_style))
    story.append(Paragraph("<b>Delete a user</b>", body_style))
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "User deleted successfully"),
        ("400", "Cannot delete the last admin in a tenant"),
    ])

    # POST /api/management/users/{id}/enable
    story.append(Paragraph("POST /api/management/users/{id}/enable", h2_style))
    story.append(Paragraph("<b>Enable or disable a user account</b>", body_style))
    dto_table("Request Body", [
        ("enabled", "boolean", "Required", "Desired enabled state"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "User enabled state updated"),
        ("400", "Cannot disable the last admin in a tenant"),
    ])

    # POST /api/management/users/{id}/roles
    story.append(Paragraph("POST /api/management/users/{id}/roles", h2_style))
    story.append(Paragraph("<b>Assign a role to a user</b>", body_style))
    dto_table("Request Body", [
        ("roleName", "string", "Required", "Role name to assign"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Role assigned successfully"),
        ("400", "Role not found, validation failed"),
    ])

    # DELETE /api/management/users/{id}/roles
    story.append(Paragraph("DELETE /api/management/users/{id}/roles", h2_style))
    story.append(Paragraph("<b>Remove a role from a user</b>", body_style))
    dto_table("Request Body", [
        ("roleName", "string", "Required", "Role name to remove"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Role removed successfully"),
        ("400", "Role not found"),
    ])

    # MFA admin endpoints
    story.append(Paragraph("POST /api/management/users/{id}/mfa/enable", h2_style))
    story.append(Paragraph("<b>Enable MFA for a user</b> \u2014 Returns the MFA secret and QR code URI for TOTP setup.", body_style))
    dto_table("Request Body", [
        ("method", "string (enum)", "Required", "MFA method (TOTP or EMAIL)"),
    ])
    story.append(Paragraph("<b>Response 200 — Success:</b>", h3_style))
    dto_table("MfaSetupResponse", [
        ("qrUri", "string", "\u2014", "OTP auth URI for QR code generation (TOTP only, null for EMAIL)"),
        ("secret", "string", "\u2014", "MFA secret key (TOTP only, null for EMAIL)"),
        ("method", "string (enum)", "\u2014", "Configured MFA method"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "MFA enabled successfully"),
        ("400", "MFA is already enabled for this user"),
        ("403", "Insufficient permissions"),
    ])

    story.append(Paragraph("POST /api/management/users/{id}/mfa/disable", h2_style))
    story.append(Paragraph("<b>Disable MFA for a user</b>", body_style))
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "MFA disabled successfully"),
        ("400", "MFA is not enabled for this user"),
        ("403", "Insufficient permissions"),
    ])

    story.append(Paragraph("POST /api/management/users/{id}/mfa/reset", h2_style))
    story.append(Paragraph("<b>Reset MFA for a user</b> \u2014 Invalidates the old secret and generates a new one.", body_style))
    dto_table("Request Body", [
        ("method", "string (enum)", "Required", "MFA method (TOTP or EMAIL)"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "MFA reset successfully"),
        ("400", "MFA is not enabled for this user"),
        ("403", "Insufficient permissions"),
    ])
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  7. TENANT MANAGEMENT ENDPOINTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("7. Tenant Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/tenants</font>", body_style))
    story.append(Spacer(1, 6))
    story.append(endpoint_table([
        ("GET", "/api/management/tenants", "List tenants (paginated, filtered)", "TENANT_READ"),
        ("GET", "/api/management/tenants/{id}", "Get tenant by ID", "TENANT_READ"),
        ("POST", "/api/management/tenants", "Create tenant", "TENANT_CREATE"),
        ("PUT", "/api/management/tenants/{id}", "Update tenant", "TENANT_UPDATE"),
        ("DELETE", "/api/management/tenants/{id}", "Delete tenant", "TENANT_DELETE"),
    ]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("TenantResponse (used by all tenant endpoints):", h3_style))
    dto_table("TenantResponse", [
        ("id", "integer", "\u2014", "Tenant ID"),
        ("name", "string", "\u2014", "Tenant name"),
        ("status", "string (enum)", "\u2014", "ACTIVE | INACTIVE | SUSPENDED"),
        ("createdAt", "timestamp", "\u2014", "Creation timestamp"),
    ])

    story.append(Paragraph("POST /api/management/tenants", h2_style))
    story.append(Paragraph("<b>Create a new tenant</b>", body_style))
    dto_table("Request Body", [
        ("name", "string", "Required, max 100 characters", "Tenant name"),
        ("status", "string (enum)", "Required", "ACTIVE, INACTIVE, or SUSPENDED"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Tenant created successfully"),
        ("400", "Tenant name already exists, validation failed"),
    ])

    story.append(Paragraph("PUT /api/management/tenants/{id}", h2_style))
    story.append(Paragraph("<b>Update an existing tenant</b>", body_style))
    dto_table("Request Body", [
        ("name", "string", "Required, max 100 characters", "Tenant name"),
        ("status", "string (enum)", "Required", "ACTIVE, INACTIVE, or SUSPENDED"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Tenant updated successfully"),
        ("400", "Validation failed"),
    ])

    story.append(Paragraph("DELETE /api/management/tenants/{id}", h2_style))
    story.append(Paragraph("<b>Delete a tenant</b>", body_style))
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Tenant deleted successfully"),
        ("400", "Tenant has active users or departments"),
    ])
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  8. ROLE MANAGEMENT ENDPOINTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("8. Role Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/roles</font>", body_style))
    story.append(Spacer(1, 6))
    story.append(endpoint_table([
        ("GET", "/api/management/roles", "List roles (paginated)", "ROLE_READ"),
        ("GET", "/api/management/roles/{id}", "Get role by ID", "ROLE_READ"),
        ("POST", "/api/management/roles", "Create role", "PLATFORM_ADMIN"),
        ("PUT", "/api/management/roles/{id}", "Update role", "PLATFORM_ADMIN"),
        ("DELETE", "/api/management/roles/{id}", "Delete role", "PLATFORM_ADMIN"),
        ("POST", "/api/management/roles/{id}/permissions", "Add permission", "PLATFORM_ADMIN"),
        ("DELETE", "/api/management/roles/{id}/permissions", "Remove permission", "PLATFORM_ADMIN"),
    ]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("RoleResponse (used by all role endpoints):", h3_style))
    dto_table("RoleResponse", [
        ("id", "integer", "\u2014", "Role ID"),
        ("name", "string", "\u2014", "Role name"),
        ("title", "string", "\u2014", "Human-readable title"),
        ("description", "string", "\u2014", "Description"),
        ("permissions", "array of strings", "\u2014", "Assigned permissions"),
    ])

    story.append(Paragraph("POST /api/management/roles", h2_style))
    story.append(Paragraph("<b>Create a new role</b>", body_style))
    dto_table("Request Body", [
        ("name", "string", "Required, max 50 characters", "Role name (unique key)"),
        ("title", "string", "Optional, max 100 characters", "Human-readable title"),
        ("description", "string", "Optional, max 255 characters", "Description"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Role created successfully"),
        ("400", "Role name already exists, validation failed"),
    ])

    story.append(Paragraph("POST /api/management/roles/{id}/permissions", h2_style))
    story.append(Paragraph("<b>Add a permission to a role</b>", body_style))
    dto_table("Request Body", [
        ("permission", "string (enum)", "Required", "Permission enum value"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Permission added successfully"),
        ("400", "Permission already assigned, validation failed"),
    ])

    story.append(Paragraph("DELETE /api/management/roles/{id}/permissions", h2_style))
    story.append(Paragraph("<b>Remove a permission from a role</b>", body_style))
    dto_table("Request Body", [
        ("permission", "string (enum)", "Required", "Permission enum value"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Permission removed successfully"),
        ("400", "Permission not found on role"),
    ])
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  9. DEPARTMENT MANAGEMENT ENDPOINTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("9. Department Management Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/departments</font>", body_style))
    story.append(Spacer(1, 6))
    story.append(endpoint_table([
        ("GET", "/api/management/departments", "List departments (paginated)", "DEPARTMENT_READ"),
        ("GET", "/api/management/departments/{id}", "Get department by ID", "DEPARTMENT_READ"),
        ("POST", "/api/management/departments", "Create department", "DEPARTMENT_CREATE"),
        ("PUT", "/api/management/departments/{id}", "Update department", "DEPARTMENT_UPDATE"),
        ("DELETE", "/api/management/departments/{id}", "Delete department", "DEPARTMENT_DELETE"),
    ]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("DepartmentResponse (used by all department endpoints):", h3_style))
    dto_table("DepartmentResponse", [
        ("id", "integer", "\u2014", "Department ID"),
        ("name", "string", "\u2014", "Department name"),
        ("tenantId", "integer", "\u2014", "Parent tenant ID"),
        ("tenantName", "string", "\u2014", "Parent tenant name"),
        ("managerIds", "array of integers", "\u2014", "Manager user IDs"),
        ("managerUsernames", "array of strings", "\u2014", "Manager usernames"),
    ])

    story.append(Paragraph("POST /api/management/departments", h2_style))
    story.append(Paragraph("<b>Create a new department</b>", body_style))
    dto_table("Request Body", [
        ("name", "string", "Required, max 100 characters", "Department name"),
        ("tenantId", "integer", "Required", "Parent tenant ID"),
        ("managerIds", "array of integers", "Optional", "Optional list of manager user IDs"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Department created successfully"),
        ("400", "Department name already exists in tenant, validation failed"),
    ])

    story.append(Paragraph("PUT /api/management/departments/{id}", h2_style))
    story.append(Paragraph("<b>Update an existing department</b>", body_style))
    dto_table("Request Body", [
        ("name", "string", "Required, max 100 characters", "Department name"),
        ("managerIds", "array of integers", "Optional", "Optional list of manager user IDs"),
    ])
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Department updated successfully"),
        ("400", "Validation failed"),
    ])

    story.append(Paragraph("DELETE /api/management/departments/{id}", h2_style))
    story.append(Paragraph("<b>Delete a department</b>", body_style))
    story.append(Paragraph("<b>Status Codes:</b>", h3_style))
    status_table([
        ("200", "Department deleted successfully"),
        ("400", "Department has active users or expenses"),
    ])
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  10. AUDIT LOG ENDPOINTS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("10. Audit Log Endpoints", h1_style))
    story.append(Paragraph("Base Path: <font face='Courier'>/api/management/audit</font>", body_style))
    story.append(Spacer(1, 6))
    story.append(endpoint_table([
        ("GET", "/api/management/audit", "List audit logs (paginated)", "AUDIT_LOG_READ"),
        ("GET", "/api/management/audit/{id}", "Get audit log by ID", "AUDIT_LOG_READ"),
    ]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("AuditLogResponse", h3_style))
    dto_table("AuditLogResponse", [
        ("id", "integer", "\u2014", "Audit log ID"),
        ("actorId", "integer", "\u2014", "Actor user ID"),
        ("actorUsername", "string", "\u2014", "Actor username"),
        ("tenantId", "integer", "\u2014", "Tenant ID"),
        ("action", "string", "\u2014", "Action performed"),
        ("resourceType", "string", "\u2014", "Resource type (USER, EXPENSE, TENANT)"),
        ("resourceId", "string", "\u2014", "Resource ID"),
        ("details", "string", "\u2014", "Additional details"),
        ("timestamp", "timestamp", "\u2014", "When the action occurred"),
    ])

    story.append(Paragraph("GET /api/management/audit", h2_style))
    story.append(Paragraph("<b>List audit logs</b> \u2014 Super admins see all logs; tenant admins see their tenant's logs.", body_style))
    story.append(Paragraph("<b>Query Parameters:</b>", h3_style))
    story.append(styled_table([
        [Paragraph("<b>Parameter</b>", body_style), Paragraph("<b>Type</b>", body_style), Paragraph("<b>Required</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("page", code_style), Paragraph("integer", body_style), Paragraph("No", body_style), Paragraph("Page number (default 0)", body_style)],
        [Paragraph("size", code_style), Paragraph("integer", body_style), Paragraph("No", body_style), Paragraph("Page size (default 20)", body_style)],
        [Paragraph("sort", code_style), Paragraph("string", body_style), Paragraph("No", body_style), Paragraph("Sort field,direction", body_style)],
    ], [100, 100, 60, 244]))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════
    #  11. ENUMERATIONS
    # ══════════════════════════════════════════════════════════════
    story.append(Paragraph("11. Enumerations", h1_style))

    story.append(Paragraph("11.1 ExpenseStatus", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Value</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("PENDING", code_style), Paragraph("Expense submitted, awaiting review", body_style)],
        [Paragraph("APPROVED", code_style), Paragraph("Expense approved by a manager", body_style)],
        [Paragraph("REJECTED", code_style), Paragraph("Expense rejected by a manager", body_style)],
        [Paragraph("CANCELLED", code_style), Paragraph("Expense cancelled by the owner", body_style)],
        [Paragraph("PROCESSED", code_style), Paragraph("Approved expense processed for payment", body_style)],
    ], [120, 384]))

    story.append(Paragraph("11.2 TenantStatus", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Value</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("ACTIVE", code_style), Paragraph("Tenant is active and operational", body_style)],
        [Paragraph("INACTIVE", code_style), Paragraph("Tenant is temporarily inactive", body_style)],
        [Paragraph("SUSPENDED", code_style), Paragraph("Tenant is suspended", body_style)],
    ], [120, 384]))

    story.append(Paragraph("11.3 MfaMethod", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Value</b>", body_style), Paragraph("<b>Description</b>", body_style)],
        [Paragraph("NONE", code_style), Paragraph("No MFA configured", body_style)],
        [Paragraph("TOTP", code_style), Paragraph("Time-based One-Time Password (authenticator app)", body_style)],
        [Paragraph("EMAIL", code_style), Paragraph("Email-based MFA codes", body_style)],
    ], [120, 384]))

    story.append(Paragraph("11.4 UserPermission", h2_style))
    story.append(styled_table([
        [Paragraph("<b>Category</b>", body_style), Paragraph("<b>Permissions</b>", body_style)],
        [Paragraph("Tenant", body_style), Paragraph("TENANT_READ, TENANT_CREATE, TENANT_UPDATE, TENANT_DELETE", body_style)],
        [Paragraph("User", body_style), Paragraph("USER_READ, USER_WRITE, USER_CREATE, USER_UPDATE, USER_DELETE, USER_ENABLE, USER_ASSIGN_ROLE", body_style)],
        [Paragraph("Role", body_style), Paragraph("ROLE_READ, ROLE_WRITE, ROLE_DELETE, ROLE_ASSIGN_PERMISSION", body_style)],
        [Paragraph("Department", body_style), Paragraph("DEPARTMENT_READ, DEPARTMENT_CREATE, DEPARTMENT_UPDATE, DEPARTMENT_DELETE", body_style)],
        [Paragraph("Expense", body_style), Paragraph("EXPENSE_READ, EXPENSE_READ_ALL, EXPENSE_CREATE, EXPENSE_UPDATE, EXPENSE_DELETE, EXPENSE_APPROVE, EXPENSE_REJECT, EXPENSE_PROCESS", body_style)],
        [Paragraph("MFA", body_style), Paragraph("MFA_MANAGE", body_style)],
        [Paragraph("Reporting &amp; Audit", body_style), Paragraph("REPORT_READ, AUDIT_LOG_READ", body_style)],
    ], [120, 384]))

    doc.build(story)
    print("PDF generated: opencode_project_api_spec.pdf")


if __name__ == "__main__":
    generate_pdf()
