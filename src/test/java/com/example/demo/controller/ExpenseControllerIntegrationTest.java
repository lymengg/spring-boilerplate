package com.example.demo.controller;

import com.example.demo.dto.ExpenseCreateRequest;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.security.cookie.AuthCookieService;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExpenseControllerIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private String employeeToken;
    private String otherDeptEmployeeToken;
    private String otherEmployeeToken;
    private String managerToken;
    private String financeToken;
    private String auditorToken;
    private String adminToken;
    private Long expenseId;
    private Long otherDeptExpenseId;
    private Long otherTenantExpenseId;

    @BeforeEach
    void setUp() {
        Tenant tenant1 = tenantRepository.save(Tenant.builder().name("Tenant 1").status(TenantStatus.ACTIVE).build());
        Tenant tenant2 = tenantRepository.save(Tenant.builder().name("Tenant 2").status(TenantStatus.ACTIVE).build());

        Department dept1 = departmentRepository.save(Department.builder().name("Dept 1").tenant(tenant1).build());
        Department dept2 = departmentRepository.save(Department.builder().name("Dept 2").tenant(tenant1).build());
        Department otherDept = departmentRepository.save(Department.builder().name("Other Dept").tenant(tenant2).build());

        Role adminRole = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("DEPARTMENT_MANAGER").orElseThrow();
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Role financeRole = roleRepository.findByName("FINANCE").orElseThrow();
        Role auditorRole = roleRepository.findByName("AUDITOR").orElseThrow();

        User employee = createUser("employee", "employee@example.com", employeeRole, tenant1, dept1);
        User otherDeptEmployee = createUser("otherdeptemp", "otherdeptemp@example.com", employeeRole, tenant1, dept2);
        User otherEmployee = createUser("otheremp", "otheremp@example.com", employeeRole, tenant2, otherDept);
        User manager = createUser("manager", "manager@example.com", managerRole, tenant1, dept1);
        User finance = createUser("finance", "finance@example.com", financeRole, tenant1, dept1);
        User auditor = createUser("auditor", "auditor@example.com", auditorRole, tenant1, dept1);
        User admin = createUser("adminexp", "adminexp@example.com", adminRole, null, null);

        dept1.getManagers().add(manager);
        departmentRepository.save(dept1);

        employeeToken = generateToken(employee.getUsername());
        otherDeptEmployeeToken = generateToken(otherDeptEmployee.getUsername());
        otherEmployeeToken = generateToken(otherEmployee.getUsername());
        managerToken = generateToken(manager.getUsername());
        financeToken = generateToken(finance.getUsername());
        auditorToken = generateToken(auditor.getUsername());
        adminToken = generateToken(admin.getUsername());

        Expense expense = Expense.builder()
                .title("Travel")
                .description("Business trip")
                .amount(BigDecimal.valueOf(100.00))
                .category("Travel")
                .status(ExpenseStatus.PENDING)
                .owner(employee)
                .department(dept1)
                .tenant(tenant1)
                .build();
        expense = expenseRepository.save(expense);
        expenseId = expense.getId();

        Expense otherDeptExpense = Expense.builder()
                .title("Stationery")
                .description("Office supplies")
                .amount(BigDecimal.valueOf(30.00))
                .category("Office")
                .status(ExpenseStatus.PENDING)
                .owner(otherDeptEmployee)
                .department(dept2)
                .tenant(tenant1)
                .build();
        otherDeptExpense = expenseRepository.save(otherDeptExpense);
        otherDeptExpenseId = otherDeptExpense.getId();

        Expense otherExpense = Expense.builder()
                .title("Other")
                .description("Other trip")
                .amount(BigDecimal.valueOf(50.00))
                .category("Food")
                .status(ExpenseStatus.PENDING)
                .owner(otherEmployee)
                .department(otherDept)
                .tenant(tenant2)
                .build();
        otherExpense = expenseRepository.save(otherExpense);
        otherTenantExpenseId = otherExpense.getId();
    }

    private User createUser(String username, String email, Role role, Tenant tenant, Department department) {
        User user = User.builder()
                .username(username)
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
        return new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @DisplayName("Employee can create an expense")
    void employeeCanCreateExpense() throws Exception {
        ExpenseCreateRequest request = ExpenseCreateRequest.builder()
                .title("Meal")
                .description("Client lunch")
                .amount(BigDecimal.valueOf(25.00))
                .category("Food")
                .build();

        mockMvc.perform(post("/api/expenses")
                        .cookie(accessCookie(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Meal"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("Employee can view their own expense")
    void employeeCanViewOwnExpense() throws Exception {
        mockMvc.perform(get("/api/expenses/{id}", expenseId)
                        .cookie(accessCookie(employeeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(expenseId));
    }

    @Test
    @DisplayName("Employee cannot view another employee's expense in a different department")
    void employeeCannotViewAnotherEmployeeExpense() throws Exception {
        mockMvc.perform(get("/api/expenses/{id}", otherDeptExpenseId)
                        .cookie(accessCookie(employeeToken)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Employee can view a teammate's expense in the same department")
    void employeeCanViewTeammateExpense() throws Exception {
        User teammate = createUser("teammate", "teammate@example.com",
                roleRepository.findByName("EMPLOYEE").orElseThrow(),
                tenantRepository.findByName("Tenant 1").orElseThrow(),
                departmentRepository.findByNameAndTenantId("Dept 1",
                        tenantRepository.findAll().stream().filter(t -> t.getName().equals("Tenant 1")).findFirst().orElseThrow().getId())
                        .orElseThrow());
        Expense teammateExpense = Expense.builder()
                .title("Team lunch")
                .description("Team lunch")
                .amount(BigDecimal.valueOf(15.00))
                .category("Food")
                .status(ExpenseStatus.PENDING)
                .owner(teammate)
                .department(departmentRepository.findByNameAndTenantId("Dept 1",
                        tenantRepository.findAll().stream().filter(t -> t.getName().equals("Tenant 1")).findFirst().orElseThrow().getId())
                        .orElseThrow())
                .tenant(tenantRepository.findByName("Tenant 1").orElseThrow())
                .build();
        teammateExpense = expenseRepository.save(teammateExpense);

        mockMvc.perform(get("/api/expenses/{id}", teammateExpense.getId())
                        .cookie(accessCookie(employeeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(teammateExpense.getId()));
    }

    @Test
    @DisplayName("Manager can approve an expense in their department")
    void managerCanApproveDepartmentExpense() throws Exception {
        mockMvc.perform(post("/api/expenses/{id}/approve", expenseId)
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Manager cannot approve an expense in another tenant")
    void managerCannotApproveOtherTenantExpense() throws Exception {
        mockMvc.perform(post("/api/expenses/{id}/approve", otherTenantExpenseId)
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Finance can process an approved expense")
    void financeCanProcessApprovedExpense() throws Exception {
        Expense expense = expenseRepository.findById(expenseId).orElseThrow();
        expense.setStatus(ExpenseStatus.APPROVED);
        expenseRepository.save(expense);

        mockMvc.perform(post("/api/expenses/{id}/process", expenseId)
                        .cookie(accessCookie(financeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));
    }

    @Test
    @DisplayName("Finance cannot approve an expense")
    void financeCannotApproveExpense() throws Exception {
        mockMvc.perform(post("/api/expenses/{id}/approve", expenseId)
                        .cookie(accessCookie(financeToken)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Cannot process a pending expense")
    void cannotProcessPendingExpense() throws Exception {
        mockMvc.perform(post("/api/expenses/{id}/process", expenseId)
                        .cookie(accessCookie(financeToken)))
                .andExpect(status().is(409));
    }

    @Test
    @DisplayName("Cannot approve an already rejected expense")
    void cannotApproveRejectedExpense() throws Exception {
        Expense expense = expenseRepository.findById(expenseId).orElseThrow();
        expense.setStatus(ExpenseStatus.REJECTED);
        expenseRepository.save(expense);

        mockMvc.perform(post("/api/expenses/{id}/approve", expenseId)
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().is(409));
    }

    @Test
    @DisplayName("Employee cannot cancel an approved expense")
    void employeeCannotCancelApprovedExpense() throws Exception {
        Expense expense = expenseRepository.findById(expenseId).orElseThrow();
        expense.setStatus(ExpenseStatus.APPROVED);
        expenseRepository.save(expense);

        mockMvc.perform(post("/api/expenses/{id}/cancel", expenseId)
                        .cookie(accessCookie(employeeToken)))
                .andExpect(status().is(409));
    }

    @Test
    @DisplayName("Employee from another tenant cannot access this tenant's expense")
    void crossTenantAccessBlocked() throws Exception {
        mockMvc.perform(get("/api/expenses/{id}", expenseId)
                        .cookie(accessCookie(otherEmployeeToken)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Super admin sees all expenses across tenants without a tenant filter")
    void superAdminSeesAllExpensesAcrossTenants() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    @Test
    @DisplayName("Super admin can filter expenses by tenant")
    void superAdminCanFilterByTenant() throws Exception {
        Tenant tenant1 = tenantRepository.findByName("Tenant 1").orElseThrow();
        mockMvc.perform(get("/api/expenses")
                        .param("tenantId", String.valueOf(tenant1.getId()))
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Finance sees all expenses in their tenant across departments")
    void financeSeesAllTenantExpenses() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .cookie(accessCookie(financeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Finance can filter tenant expenses by department")
    void financeCanFilterByDepartment() throws Exception {
        Department dept2 = departmentRepository.findByNameAndTenantId("Dept 2",
                tenantRepository.findAll().stream().filter(t -> t.getName().equals("Tenant 1")).findFirst().orElseThrow().getId())
                .orElseThrow();
        mockMvc.perform(get("/api/expenses")
                        .param("departmentId", String.valueOf(dept2.getId()))
                        .cookie(accessCookie(financeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("Employee sees only their department expenses")
    void employeeSeesOnlyDepartmentExpenses() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .cookie(accessCookie(employeeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("Auditor cannot process an expense")
    void auditorCannotProcessExpense() throws Exception {
        Expense expense = expenseRepository.findById(expenseId).orElseThrow();
        expense.setStatus(ExpenseStatus.APPROVED);
        expenseRepository.save(expense);

        mockMvc.perform(post("/api/expenses/{id}/process", expenseId)
                        .cookie(accessCookie(auditorToken)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Manager sees expenses from their department")
    void managerSeesDepartmentExpenses() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    @DisplayName("Manager can approve expense in any of their managed departments")
    void managerCanApproveExpenseInAnyManagedDepartment() throws Exception {
        Department dept2 = departmentRepository.findByNameAndTenantId("Dept 2",
                tenantRepository.findAll().stream().filter(t -> t.getName().equals("Tenant 1")).findFirst().orElseThrow().getId())
                .orElseThrow();
        User multiManager = userRepository.findByUsername("manager").orElseThrow();
        dept2.getManagers().add(multiManager);
        departmentRepository.save(dept2);

        mockMvc.perform(post("/api/expenses/{id}/approve", otherDeptExpenseId)
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }
}
