package com.example.demo.controller;

import com.example.demo.entity.Department;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.TenantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.cookie.AuthCookieManager;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DepartmentManagementControllerIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String tenantAdminToken;
    private Tenant tenant;
    private Tenant otherTenant;
    private User manager1;
    private User manager2;
    private User employee;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        Role tenantAdminRole = roleRepository.findByName("TENANT_ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("DEPARTMENT_MANAGER").orElseThrow();
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        tenant = tenantRepository.save(Tenant.builder().name("Test Tenant").build());
        otherTenant = tenantRepository.save(Tenant.builder().name("Second Tenant").build());

        User admin = createUser("admin@example.com", adminRole, null);
        User tenantAdmin = createUser("tenantadmin@example.com", tenantAdminRole, tenant);
        manager1 = createUser("manager1@example.com", managerRole, tenant);
        manager2 = createUser("manager2@example.com", managerRole, tenant);
        employee = createUser("employee@example.com", employeeRole, tenant);

        adminToken = generateToken(admin.getEmail());
        tenantAdminToken = generateToken(tenantAdmin.getEmail());
    }

    private User createUser(String email, Role role, Tenant userTenant) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .tenant(userTenant)
                .build();
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private String generateToken(String username) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        return jwtTokenProvider.generateAccessToken(authentication);
    }

    private Cookie accessCookie(String token) {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @DisplayName("Admin can create department with multiple managers")
    void adminCanCreateDepartmentWithMultipleManagers() throws Exception {
        mockMvc.perform(post("/api/management/departments")
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Engineering",
                                "tenantId", tenant.getId(),
                                "managerIds", java.util.List.of(manager1.getId(), manager2.getId())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Engineering"))
                .andExpect(jsonPath("$.data.managerIds.length()").value(2));
    }

    @Test
    @DisplayName("Admin can create department with empty manager list")
    void adminCanCreateDepartmentWithNoManagers() throws Exception {
        mockMvc.perform(post("/api/management/departments")
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Sales",
                                "tenantId", tenant.getId()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Sales"))
                .andExpect(jsonPath("$.data.managerIds.length()").value(0));
    }

    @Test
    @DisplayName("Admin can update department managers")
    void adminCanUpdateDepartmentManagers() throws Exception {
        Department dept = departmentRepository.save(
                Department.builder().name("Marketing").tenant(tenant).build());

        mockMvc.perform(put("/api/management/departments/{id}", dept.getId())
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Marketing",
                                "managerIds", java.util.List.of(manager1.getId())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.managerIds.length()").value(1))
                .andExpect(jsonPath("$.data.managerIds[0]").value(manager1.getId()));
    }

    @Test
    @DisplayName("Rejects manager from another tenant")
    void rejectsManagerFromAnotherTenant() throws Exception {
        Tenant otherTenant = tenantRepository.save(Tenant.builder().name("Other Tenant").build());
        User otherManager = createUser("other@example.com",
                roleRepository.findByName("DEPARTMENT_MANAGER").orElseThrow(), otherTenant);

        mockMvc.perform(post("/api/management/departments")
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Finance",
                                "tenantId", tenant.getId(),
                                "managerIds", java.util.List.of(otherManager.getId())
                        ))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Duplicate department name in same tenant is rejected")
    void duplicateDepartmentNameRejected() throws Exception {
        departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());

        mockMvc.perform(post("/api/management/departments")
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "HR",
                                "tenantId", tenant.getId()
                        ))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin sees all departments when listing")
    void adminSeesAllDepartments() throws Exception {
        departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());
        departmentRepository.save(Department.builder().name("Sales").tenant(otherTenant).build());

        mockMvc.perform(get("/api/management/departments")
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Tenant admin sees only own tenant departments when listing")
    void tenantAdminSeesOnlyOwnTenantDepartments() throws Exception {
        departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());
        departmentRepository.save(Department.builder().name("Sales").tenant(otherTenant).build());

        mockMvc.perform(get("/api/management/departments")
                        .cookie(accessCookie(tenantAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("HR"));
    }

    @Test
    @DisplayName("Admin can get department by id")
    void adminCanGetDepartmentById() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());

        mockMvc.perform(get("/api/management/departments/{id}", dept.getId())
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("HR"));
    }

    @Test
    @DisplayName("Cross-tenant department access returns 400")
    void crossTenantDepartmentAccessReturns400() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("HR").tenant(otherTenant).build());

        mockMvc.perform(get("/api/management/departments/{id}", dept.getId())
                        .cookie(accessCookie(tenantAdminToken)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Getting a nonexistent department returns 400")
    void getNonexistentDepartmentReturns400() throws Exception {
        mockMvc.perform(get("/api/management/departments/{id}", 999999L)
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can update department name")
    void adminCanUpdateDepartmentName() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());

        mockMvc.perform(put("/api/management/departments/{id}", dept.getId())
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "PeopleOps"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("PeopleOps"));
    }

    @Test
    @DisplayName("Updating department to a duplicate name returns 400")
    void updateDepartmentDuplicateNameReturns400() throws Exception {
        departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());
        Department dept = departmentRepository.save(Department.builder().name("Sales").tenant(tenant).build());

        mockMvc.perform(put("/api/management/departments/{id}", dept.getId())
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "HR"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can delete department")
    void adminCanDeleteDepartment() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());

        mockMvc.perform(delete("/api/management/departments/{id}", dept.getId())
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Non-manager cannot delete department")
    void nonManagerCannotDeleteDepartment() throws Exception {
        Department dept = departmentRepository.save(Department.builder().name("HR").tenant(tenant).build());
        String managerToken = generateToken(manager2.getEmail());

        mockMvc.perform(delete("/api/management/departments/{id}", dept.getId())
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Cannot create department in a tenant the user cannot manage")
    void cannotCreateDepartmentInUnmanageableTenant() throws Exception {
        mockMvc.perform(post("/api/management/departments")
                        .cookie(accessCookie(tenantAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Finance",
                                "tenantId", otherTenant.getId()
                        ))))
                .andExpect(status().is(403));
    }
}
