package com.example.demo.controller;

import com.example.demo.dto.UserCreateRequest;
import com.example.demo.entity.Department;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.TenantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private String adminToken;
    private String managerToken;
    private String userToken;
    private Long regularUserId;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("USER_MANAGER").orElseThrow();
        Role userRole = roleRepository.findByName("USER").orElseThrow();

        Tenant tenant = tenantRepository.save(Tenant.builder().name("Test Tenant").build());

        User admin = createUser("adminuser", "admin@example.com", adminRole, null);
        User manager = createUser("manager", "manager@example.com", managerRole, tenant);
        User user = createUser("testuser", "test@example.com", userRole, tenant);
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

    @Test
    @DisplayName("Admin can list users")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    @Test
    @DisplayName("User manager can list users in their tenant")
    void userManagerCanListUsers() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("Regular user cannot list users")
    void regularUserCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .header("Authorization", "Bearer " + userToken))
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
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("User manager cannot delete user")
    void userManagerCannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/management/users/{id}", regularUserId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Admin cannot delete themselves")
    void adminCannotDeleteSelf() throws Exception {
        User admin = userRepository.findByUsername("adminuser").orElseThrow();
        mockMvc.perform(delete("/api/management/users/{id}", admin.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin cannot delete last admin")
    void adminCannotDeleteLastAdmin() throws Exception {
        // delete the other users so only admin remains
        userRepository.delete(userRepository.findByUsername("manager").orElseThrow());
        userRepository.delete(userRepository.findByUsername("testuser").orElseThrow());

        User admin = userRepository.findByUsername("adminuser").orElseThrow();
        mockMvc.perform(delete("/api/management/users/{id}", admin.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager can update user")
    void userManagerCanUpdateUser() throws Exception {
        mockMvc.perform(put("/api/management/users/{id}", regularUserId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Updated"));
    }

    @Test
    @DisplayName("Regular user cannot update other user")
    void regularUserCannotUpdate() throws Exception {
        mockMvc.perform(put("/api/management/users/{id}", regularUserId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Updated"))))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("User manager can assign USER role to a user in their tenant")
    void userManagerCanAssignUserRole() throws Exception {
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
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray());
    }

    @Test
    @DisplayName("User manager cannot assign ADMIN role")
    void userManagerCannotAssignAdminRole() throws Exception {
        mockMvc.perform(post("/api/management/users/{id}/roles", regularUserId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "ADMIN"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can assign ADMIN role")
    void adminCanAssignAdminRole() throws Exception {
        User newUser = createUser("newuser", "new@example.com", roleRepository.findByName("USER").orElseThrow(), null);
        mockMvc.perform(post("/api/management/users/{id}/roles", newUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "ADMIN"))))
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
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "USER_MANAGER"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager cannot update admin")
    void userManagerCannotUpdateAdmin() throws Exception {
        User admin = userRepository.findByUsername("adminuser").orElseThrow();

        mockMvc.perform(put("/api/management/users/{id}", admin.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Hacked"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager cannot remove role from admin")
    void userManagerCannotRemoveRoleFromAdmin() throws Exception {
        User admin = userRepository.findByUsername("adminuser").orElseThrow();

        mockMvc.perform(delete("/api/management/users/{id}/roles", admin.getId())
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "ADMIN"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can create a user")
    void adminCanCreateUser() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("newuser")
                .email("newuser@example.com")
                .password("SecurePass123!")
                .firstName("New")
                .lastName("User")
                .roleName("USER")
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("newuser"))
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    @DisplayName("Admin can create a user with tenant and department")
    void adminCanCreateUserWithTenantAndDepartment() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();
        Department dept = departmentRepository.save(Department.builder()
                .name("Engineering")
                .tenant(tenant)
                .build());

        UserCreateRequest request = UserCreateRequest.builder()
                .username("deptuser")
                .email("deptuser@example.com")
                .password("SecurePass123!")
                .firstName("Dept")
                .lastName("User")
                .roleName("USER")
                .tenantId(tenant.getId())
                .departmentId(dept.getId())
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("deptuser"))
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    @DisplayName("User manager can create a user in their own tenant")
    void userManagerCanCreateUserInOwnTenant() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();

        UserCreateRequest request = UserCreateRequest.builder()
                .username("manageduser")
                .email("manageduser@example.com")
                .password("SecurePass123!")
                .firstName("Managed")
                .lastName("User")
                .roleName("USER")
                .tenantId(tenant.getId())
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("manageduser"));
    }

    @Test
    @DisplayName("User manager cannot create user in a different tenant")
    void userManagerCannotCreateUserInDifferentTenant() throws Exception {
        Tenant otherTenant = tenantRepository.save(Tenant.builder().name("Other Tenant").build());

        UserCreateRequest request = UserCreateRequest.builder()
                .username("crossuser")
                .email("crossuser@example.com")
                .password("SecurePass123!")
                .firstName("Cross")
                .lastName("User")
                .roleName("USER")
                .tenantId(otherTenant.getId())
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User manager cannot create user with ADMIN role")
    void userManagerCannotCreateAdminUser() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();

        UserCreateRequest request = UserCreateRequest.builder()
                .username("adminuser2")
                .email("adminuser2@example.com")
                .password("SecurePass123!")
                .firstName("Admin")
                .lastName("User")
                .roleName("ADMIN")
                .tenantId(tenant.getId())
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager cannot create user with USER_MANAGER role")
    void userManagerCannotCreateUserManager() throws Exception {
        Tenant tenant = tenantRepository.findByName("Test Tenant").orElseThrow();

        UserCreateRequest request = UserCreateRequest.builder()
                .username("mgruser")
                .email("mgruser@example.com")
                .password("SecurePass123!")
                .firstName("Mgr")
                .lastName("User")
                .roleName("USER_MANAGER")
                .tenantId(tenant.getId())
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can create user with USER_MANAGER role")
    void adminCanCreateUserManager() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("newmanager")
                .email("newmanager@example.com")
                .password("SecurePass123!")
                .firstName("New")
                .lastName("Manager")
                .roleName("USER_MANAGER")
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("USER_MANAGER"));
    }

    @Test
    @DisplayName("Regular user cannot create users")
    void regularUserCannotCreateUser() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("anotheruser")
                .email("another@example.com")
                .password("SecurePass123!")
                .firstName("Another")
                .lastName("User")
                .roleName("USER")
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated cannot create users")
    void unauthenticatedCannotCreateUser() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("anonuser")
                .email("anon@example.com")
                .password("SecurePass123!")
                .firstName("Anon")
                .lastName("User")
                .roleName("USER")
                .build();

        mockMvc.perform(post("/api/management/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Creating user with duplicate username returns error")
    void createDuplicateUsernameReturnsError() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("adminuser")
                .email("adminuser2@example.com")
                .password("SecurePass123!")
                .firstName("Dup")
                .lastName("User")
                .roleName("USER")
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Creating user with missing fields returns 400")
    void createWithMissingFieldsReturns400() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("")
                .email("valid@example.com")
                .password("SecurePass123!")
                .roleName("USER")
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Creating user with non-existent role returns 400")
    void createWithNonExistentRoleReturns400() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("badrole")
                .email("badrole@example.com")
                .password("SecurePass123!")
                .firstName("Bad")
                .lastName("Role")
                .roleName("NONEXISTENT_ROLE")
                .build();

        mockMvc.perform(post("/api/management/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Register endpoint is no longer available")
    void registerEndpointNotAvailable() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("reguser")
                .email("reguser@example.com")
                .password("SecurePass123!")
                .firstName("Reg")
                .lastName("User")
                .roleName("USER")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
