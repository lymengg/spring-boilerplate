package com.example.demo.controller;

import com.example.demo.constants.AuditActions;
import com.example.demo.dto.ExpenseCreateRequest;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.security.cookie.AuthCookieManager;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private String superAdminToken;
    private String tenantAdmin1Token;
    private String employeeToken;
    private Long tenant1Id;
    private Long tenant1AuditLogId;
    private Long tenant2AuditLogId;

    @BeforeEach
    void setUp() throws Exception {
        Tenant tenant1 = tenantRepository.save(Tenant.builder().name("Tenant 1").status(TenantStatus.ACTIVE).build());
        Tenant tenant2 = tenantRepository.save(Tenant.builder().name("Tenant 2").status(TenantStatus.ACTIVE).build());
        tenant1Id = tenant1.getId();

        Department dept1 = departmentRepository.save(Department.builder().name("Dept 1").tenant(tenant1).build());
        Department dept2 = departmentRepository.save(Department.builder().name("Dept 2").tenant(tenant2).build());

        Role adminRole = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        Role tenantAdminRole = roleRepository.findByName("TENANT_ADMIN").orElseThrow();
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        User superAdmin = createUser("auditsuper@example.com", adminRole, null, null);
        User tenantAdmin1 = createUser("auditadmin1@example.com", tenantAdminRole, tenant1, null);
        User employee = createUser("auditemp@example.com", employeeRole, tenant1, dept1);
        User employee2 = createUser("auditemp2@example.com", employeeRole, tenant2, dept2);

        superAdminToken = generateToken(superAdmin.getEmail());
        tenantAdmin1Token = generateToken(tenantAdmin1.getEmail());
        employeeToken = generateToken(employee.getEmail());

        tenant1AuditLogId = createExpenseAndGetAuditLogId(employeeToken, "Tenant 1 Expense");
        tenant2AuditLogId = createExpenseAndGetAuditLogId(generateToken(employee2.getEmail()), "Tenant 2 Expense");
    }

    private User createUser(String email, Role role, Tenant tenant, Department department) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .tenant(tenant)
                .department(department)
                .build();
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private String generateToken(String username) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        return jwtTokenProvider.generateAccessToken(authentication);
    }

    private Cookie accessCookie(String token) {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, token);
    }

    private Long createExpenseAndGetAuditLogId(String token, String title) throws Exception {
        ExpenseCreateRequest request = ExpenseCreateRequest.builder()
                .title(title)
                .description("Audit test expense")
                .amount(BigDecimal.valueOf(50.00))
                .category("Office")
                .build();

        MvcResult result = mockMvc.perform(post("/api/expenses")
                        .cookie(accessCookie(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long expenseId = json.get("data").get("id").asLong();

        return auditLogRepository.findAll().stream()
                .filter(log -> AuditActions.EXPENSE_CREATED.equals(log.getAction())
                        && String.valueOf(expenseId).equals(log.getResourceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No audit log row created for expense " + expenseId))
                .getId();
    }

    @Test
    @DisplayName("Unauthenticated request to audit logs returns 401")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/management/audit"))
                .andExpect(status().is(401));
    }

    @Test
    @DisplayName("User without AUDIT_LOG_READ permission gets 403")
    void userWithoutAuditLogReadGets403() throws Exception {
        mockMvc.perform(get("/api/management/audit")
                        .cookie(accessCookie(employeeToken)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Super admin lists audit logs across all tenants")
    void superAdminListsAuditLogs() throws Exception {
        mockMvc.perform(get("/api/management/audit")
                        .cookie(accessCookie(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Tenant admin lists only their own tenant's audit logs")
    void tenantAdminListsOnlyOwnTenantLogs() throws Exception {
        mockMvc.perform(get("/api/management/audit")
                        .cookie(accessCookie(tenantAdmin1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].tenantId").value(tenant1Id));
    }

    @Test
    @DisplayName("Super admin gets an audit log by id")
    void superAdminGetsAuditLogById() throws Exception {
        mockMvc.perform(get("/api/management/audit/{id}", tenant1AuditLogId)
                        .cookie(accessCookie(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tenant1AuditLogId));
    }

    @Test
    @DisplayName("Tenant admin reading another tenant's audit log gets 400 with no tenant leak")
    void crossTenantAuditLogReadReturns400() throws Exception {
        mockMvc.perform(get("/api/management/audit/{id}", tenant2AuditLogId)
                        .cookie(accessCookie(tenantAdmin1Token)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Reading a nonexistent audit log returns 400")
    void nonexistentAuditLogReturns400() throws Exception {
        mockMvc.perform(get("/api/management/audit/{id}", 999999L)
                        .cookie(accessCookie(superAdminToken)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("A business action creates a matching audit log row end-to-end")
    void businessActionCreatesAuditRow() throws Exception {
        ExpenseCreateRequest request = ExpenseCreateRequest.builder()
                .title("Audit Wired Expense")
                .description("Proves record() is wired end-to-end")
                .amount(BigDecimal.valueOf(75.00))
                .category("Travel")
                .build();

        MvcResult result = mockMvc.perform(post("/api/expenses")
                        .cookie(accessCookie(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long expenseId = json.get("data").get("id").asLong();

        AuditLog auditLog = auditLogRepository.findAll().stream()
                .filter(log -> AuditActions.EXPENSE_CREATED.equals(log.getAction())
                        && String.valueOf(expenseId).equals(log.getResourceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No audit row for expense " + expenseId));

        assertThat(auditLog.getActorEmail()).isEqualTo("auditemp@example.com");
        assertThat(auditLog.getTenantId()).isEqualTo(tenant1Id);
        assertThat(auditLog.getResourceType()).isEqualTo(AuditActions.RESOURCE_EXPENSE);
        assertThat(auditLog.getDetails()).isEqualTo("Expense created");
    }
}
