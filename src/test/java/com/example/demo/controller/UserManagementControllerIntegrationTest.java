package com.example.demo.controller;

import com.example.demo.entity.Department;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.TenantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.cookie.AuthCookieService;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.example.demo.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserManagementControllerIntegrationTest {

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

    @MockBean
    private TokenService tokenService;

    private String adminToken;
    private String managerToken;
    private String userToken;
    private Long regularUserId;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("USER_MANAGER").orElseThrow();
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        Tenant tenant = tenantRepository.save(Tenant.builder().name("Test Tenant").build());
        Department dept = departmentRepository.save(Department.builder().name("Test Dept").tenant(tenant).build());

        User admin = createUser("adminuser", "admin@example.com", adminRole, null);
        User manager = createUser("manager", "manager@example.com", managerRole, tenant);
        User user = createUser("testuser", "test@example.com", employeeRole, tenant);
        regularUserId = user.getId();

        adminToken = generateToken(admin.getUsername());
        managerToken = generateToken(manager.getUsername());
        userToken = generateToken(user.getUsername());
    }

    private User createUser(String username, String email, Role role, Tenant tenant) {
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
        return new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @DisplayName("Admin can list users")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    @Test
    @DisplayName("User manager can list users in their tenant")
    void userManagerCanListUsers() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Regular user cannot list users")
    void regularUserCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .cookie(accessCookie(userToken)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Unauthenticated cannot access management")
    void unauthenticatedCannotAccess() throws Exception {
        mockMvc.perform(get("/api/management/users"))
                .andExpect(status().is(401));
    }

    @Test
    @DisplayName("Admin can delete user")
    void adminCanDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/management/users/{id}", regularUserId)
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("User manager cannot delete user")
    void userManagerCannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/management/users/{id}", regularUserId)
                        .cookie(accessCookie(managerToken)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Admin cannot delete themselves")
    void adminCannotDeleteSelf() throws Exception {
        User admin = userRepository.findByUsername("adminuser").orElseThrow();
        mockMvc.perform(delete("/api/management/users/{id}", admin.getId())
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin cannot delete last admin")
    void adminCannotDeleteLastAdmin() throws Exception {
        userRepository.delete(userRepository.findByUsername("manager").orElseThrow());
        userRepository.delete(userRepository.findByUsername("testuser").orElseThrow());

        User admin = userRepository.findByUsername("adminuser").orElseThrow();
        mockMvc.perform(delete("/api/management/users/{id}", admin.getId())
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager can update user")
    void userManagerCanUpdateUser() throws Exception {
        mockMvc.perform(put("/api/management/users/{id}", regularUserId)
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Updated"));
    }

    @Test
    @DisplayName("Admin can reassign user to a different department in the same tenant")
    void adminCanReassignDepartment() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();
        Department newDept = departmentRepository.save(Department.builder().name("New Dept").tenant(tenant).build());

        mockMvc.perform(put("/api/management/users/{id}", regularUserId)
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("departmentId", newDept.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentId").value(newDept.getId()))
                .andExpect(jsonPath("$.data.departmentName").value("New Dept"));
    }

    @Test
    @DisplayName("Cannot reassign user to a department in a different tenant")
    void cannotReassignToOtherTenantDepartment() throws Exception {
        Tenant otherTenant = tenantRepository.save(Tenant.builder().name("Other Tenant 2").build());
        Department otherDept = departmentRepository.save(Department.builder().name("Other Dept 2").tenant(otherTenant).build());

        mockMvc.perform(put("/api/management/users/{id}", regularUserId)
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("departmentId", otherDept.getId()))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Regular user cannot update other user")
    void regularUserCannotUpdate() throws Exception {
        mockMvc.perform(put("/api/management/users/{id}", regularUserId)
                        .cookie(accessCookie(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Updated"))))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("User manager can assign EMPLOYEE role to a user in their tenant")
    void userManagerCanAssignEmployeeRole() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();
        User noRoleUser = User.builder()
                .username("norole")
                .email("norole@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("No")
                .lastName("Role")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .tenant(tenant)
                .build();
        noRoleUser = userRepository.save(noRoleUser);

        mockMvc.perform(post("/api/management/users/{id}/roles", noRoleUser.getId())
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray());
    }

    @Test
    @DisplayName("User manager cannot assign PLATFORM_ADMIN role")
    void userManagerCannotAssignAdminRole() throws Exception {
        mockMvc.perform(post("/api/management/users/{id}/roles", regularUserId)
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "PLATFORM_ADMIN"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can assign PLATFORM_ADMIN role")
    void adminCanAssignAdminRole() throws Exception {
        User newUser = createUser("newuser", "new@example.com", roleRepository.findByName("EMPLOYEE").orElseThrow(), null);
        mockMvc.perform(post("/api/management/users/{id}/roles", newUser.getId())
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "PLATFORM_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("User manager cannot assign USER_MANAGER role")
    void userManagerCannotAssignUserManagerRole() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();
        User noRoleUser = User.builder()
                .username("norole2")
                .email("norole2@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("No")
                .lastName("Role")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .tenant(tenant)
                .build();
        noRoleUser = userRepository.save(noRoleUser);

        mockMvc.perform(post("/api/management/users/{id}/roles", noRoleUser.getId())
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "USER_MANAGER"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager cannot update admin")
    void userManagerCannotUpdateAdmin() throws Exception {
        User admin = userRepository.findByUsername("adminuser").orElseThrow();

        mockMvc.perform(put("/api/management/users/{id}", admin.getId())
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Hacked"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager cannot remove role from admin")
    void userManagerCannotRemoveRoleFromAdmin() throws Exception {
        User admin = userRepository.findByUsername("adminuser").orElseThrow();

        mockMvc.perform(delete("/api/management/users/{id}/roles", admin.getId())
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "PLATFORM_ADMIN"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can create a user with role and tenant")
    void adminCanCreateUser() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();
        Department dept = departmentRepository.findByNameAndTenantId("Test Dept", tenant.getId()).orElseThrow();
        Map<String, Object> body = Map.of(
                "username", "createduser",
                "email", "created@example.com",
                "password", "SecurePass123!",
                "firstName", "Created",
                "lastName", "User",
                "roleName", "EMPLOYEE",
                "tenantId", tenant.getId(),
                "departmentId", dept.getId());

        mockMvc.perform(post("/api/management/users")
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("createduser"))
                .andExpect(jsonPath("$.data.email").value("created@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("User manager can create a user in their tenant with default role")
    void userManagerCanCreateUserInTenant() throws Exception {
        Department dept = departmentRepository.findByNameAndTenantId("Test Dept",
                tenantRepository.findByName("Test Tenant").orElseThrow().getId()).orElseThrow();
        Map<String, Object> body = Map.of(
                "username", "createduser",
                "email", "created@example.com",
                "password", "SecurePass123!",
                "departmentId", dept.getId());

        mockMvc.perform(post("/api/management/users")
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("createduser"))
                .andExpect(jsonPath("$.data.roles[0]").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("User manager cannot create a user with ADMIN role")
    void userManagerCannotCreateAdmin() throws Exception {
        Department dept = departmentRepository.findByNameAndTenantId("Test Dept",
                tenantRepository.findByName("Test Tenant").orElseThrow().getId()).orElseThrow();
        Map<String, Object> body = Map.of(
                "username", "createduser",
                "email", "created@example.com",
                "password", "SecurePass123!",
                "roleName", "PLATFORM_ADMIN",
                "departmentId", dept.getId());

        mockMvc.perform(post("/api/management/users")
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager cannot create a user in a different tenant")
    void userManagerCannotCreateUserInOtherTenant() throws Exception {
        Tenant otherTenant = tenantRepository.save(Tenant.builder().name("Other Tenant").build());
        Department otherDept = departmentRepository.save(Department.builder().name("Other Dept").tenant(otherTenant).build());
        Map<String, Object> body = Map.of(
                "username", "createduser",
                "email", "created@example.com",
                "password", "SecurePass123!",
                "tenantId", otherTenant.getId(),
                "departmentId", otherDept.getId());

        mockMvc.perform(post("/api/management/users")
                        .cookie(accessCookie(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Regular user cannot create a user")
    void regularUserCannotCreateUser() throws Exception {
        Department dept = departmentRepository.findByNameAndTenantId("Test Dept",
                tenantRepository.findByName("Test Tenant").orElseThrow().getId()).orElseThrow();
        Map<String, Object> body = Map.of(
                "username", "createduser",
                "email", "created@example.com",
                "password", "SecurePass123!",
                "departmentId", dept.getId());

        mockMvc.perform(post("/api/management/users")
                        .cookie(accessCookie(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Unauthenticated cannot create a user")
    void unauthenticatedCannotCreateUser() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "createduser",
                "email", "created@example.com",
                "password", "SecurePass123!");

        mockMvc.perform(post("/api/management/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(401));
    }

    @Test
    @DisplayName("Creating a user with a duplicate username returns 400")
    void duplicateUsernameReturns400() throws Exception {
        Department dept = departmentRepository.findByNameAndTenantId("Test Dept",
                tenantRepository.findByName("Test Tenant").orElseThrow().getId()).orElseThrow();
        Map<String, Object> body = Map.of(
                "username", "testuser",
                "email", "unique@example.com",
                "password", "SecurePass123!",
                "departmentId", dept.getId());

        mockMvc.perform(post("/api/management/users")
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Weak password on user creation returns 400")
    void weakPasswordReturns400() throws Exception {
        Department dept = departmentRepository.findByNameAndTenantId("Test Dept",
                tenantRepository.findByName("Test Tenant").orElseThrow().getId()).orElseThrow();
        Map<String, Object> body = Map.of(
                "username", "createduser",
                "email", "created@example.com",
                "password", "weak",
                "departmentId", dept.getId());

        mockMvc.perform(post("/api/management/users")
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin can enable TOTP MFA for a user")
    void adminCanEnableMfa() throws Exception {
        mockMvc.perform(post("/api/management/users/{id}/mfa/enable", regularUserId)
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("method", "TOTP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("TOTP"))
                .andExpect(jsonPath("$.data.qrUri").isNotEmpty())
                .andExpect(jsonPath("$.data.secret").isNotEmpty());
    }

    @Test
    @DisplayName("Admin can disable MFA for a user")
    void adminCanDisableMfa() throws Exception {
        User user = userRepository.findById(regularUserId).orElseThrow();
        user.setMfaEnabled(true);
        user.setMfaMethod(com.example.demo.entity.MfaMethod.TOTP);
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        userRepository.save(user);

        mockMvc.perform(post("/api/management/users/{id}/mfa/disable", regularUserId)
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isOk());

        User updated = userRepository.findById(regularUserId).orElseThrow();
        assertThat(updated.getMfaEnabled()).isFalse();
        assertThat(updated.getMfaMethod()).isEqualTo(com.example.demo.entity.MfaMethod.NONE);
        assertThat(updated.getMfaSecret()).isNull();
    }

    @Test
    @DisplayName("Admin can reset MFA for a user with TOTP")
    void adminCanResetMfa() throws Exception {
        User user = userRepository.findById(regularUserId).orElseThrow();
        user.setMfaEnabled(true);
        user.setMfaMethod(com.example.demo.entity.MfaMethod.TOTP);
        user.setMfaSecret("OLDSECRET1234567890");
        userRepository.save(user);

        mockMvc.perform(post("/api/management/users/{id}/mfa/reset", regularUserId)
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("method", "TOTP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("TOTP"))
                .andExpect(jsonPath("$.data.secret").isNotEmpty());
    }

    @Test
    @DisplayName("Regular user cannot manage MFA")
    void regularUserCannotManageMfa() throws Exception {
        mockMvc.perform(post("/api/management/users/{id}/mfa/enable", regularUserId)
                        .cookie(accessCookie(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("method", "TOTP"))))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Enabling MFA on already-enabled user returns 409")
    void enableMfaWhenAlreadyEnabledReturns409() throws Exception {
        User user = userRepository.findById(regularUserId).orElseThrow();
        user.setMfaEnabled(true);
        user.setMfaMethod(com.example.demo.entity.MfaMethod.TOTP);
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        userRepository.save(user);

        mockMvc.perform(post("/api/management/users/{id}/mfa/enable", regularUserId)
                        .cookie(accessCookie(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("method", "TOTP"))))
                .andExpect(status().is(409));
    }

    @Test
    @DisplayName("Disabling MFA when not enabled returns 409")
    void disableMfaWhenNotEnabledReturns409() throws Exception {
        mockMvc.perform(post("/api/management/users/{id}/mfa/disable", regularUserId)
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().is(409));
    }

    @Test
    @DisplayName("Unauthenticated cannot manage MFA")
    void unauthenticatedCannotManageMfa() throws Exception {
        mockMvc.perform(post("/api/management/users/{id}/mfa/enable", regularUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("method", "TOTP"))))
                .andExpect(status().is(401));
    }
}
