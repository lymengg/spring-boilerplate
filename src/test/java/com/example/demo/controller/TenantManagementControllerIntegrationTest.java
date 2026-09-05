package com.example.demo.controller;

import com.example.demo.dto.TenantCreateRequest;
import com.example.demo.dto.TenantUpdateRequest;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.TenantStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.TenantRepository;
import com.example.demo.repository.UserRepository;
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

import java.util.HashSet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantManagementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private String platformAdminToken;
    private String tenantAdminToken;
    private String employeeToken;
    private Long tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.save(
                Tenant.builder().name("Acme Corp").status(TenantStatus.ACTIVE).build());
        tenantId = tenant.getId();

        com.example.demo.entity.Role platformAdminRole = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        com.example.demo.entity.Role tenantAdminRole = roleRepository.findByName("TENANT_ADMIN").orElseThrow();
        com.example.demo.entity.Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();

        User platformAdmin = createUser("platformadmin", "pa@example.com", platformAdminRole, null);
        User tenantAdminUser = createUser("tenantadmin", "ta@example.com", tenantAdminRole, tenant);
        User employee = createUser("employee", "emp@example.com", employeeRole, tenant);

        platformAdminToken = generateToken(platformAdmin.getUsername());
        tenantAdminToken = generateToken(tenantAdminUser.getUsername());
        employeeToken = generateToken(employee.getUsername());
    }

    @Test
    @DisplayName("Platform admin can list all tenants")
    void platformAdminCanListTenants() throws Exception {
        mockMvc.perform(get("/api/management/tenants")
                        .cookie(accessCookie(platformAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Acme Corp"));
    }

    @Test
    @DisplayName("Tenant admin cannot list tenants")
    void tenantAdminCannotListTenants() throws Exception {
        mockMvc.perform(get("/api/management/tenants")
                        .cookie(accessCookie(tenantAdminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Employee cannot list tenants")
    void employeeCannotListTenants() throws Exception {
        mockMvc.perform(get("/api/management/tenants")
                        .cookie(accessCookie(employeeToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Platform admin can search tenants by name")
    void platformAdminCanSearchTenantsByName() throws Exception {
        tenantRepository.save(Tenant.builder().name("Beta Industries").status(TenantStatus.ACTIVE).build());

        mockMvc.perform(get("/api/management/tenants")
                        .param("name", "acme")
                        .cookie(accessCookie(platformAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Acme Corp"));
    }

    @Test
    @DisplayName("Search returns empty when no match")
    void searchReturnsEmptyWhenNoMatch() throws Exception {
        mockMvc.perform(get("/api/management/tenants")
                        .param("name", "NonExistent")
                        .cookie(accessCookie(platformAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("Search is case-insensitive")
    void searchIsCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/management/tenants")
                        .param("name", "ACME")
                        .cookie(accessCookie(platformAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Acme Corp"));
    }

    @Test
    @DisplayName("Platform admin can get tenant by ID")
    void platformAdminCanGetTenantById() throws Exception {
        mockMvc.perform(get("/api/management/tenants/{id}", tenantId)
                        .cookie(accessCookie(platformAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Corp"));
    }

    @Test
    @DisplayName("Tenant admin can get their own tenant by ID")
    void tenantAdminCanGetOwnTenantById() throws Exception {
        mockMvc.perform(get("/api/management/tenants/{id}", tenantId)
                        .cookie(accessCookie(tenantAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Corp"));
    }

    @Test
    @DisplayName("Platform admin can create a tenant")
    void platformAdminCanCreateTenant() throws Exception {
        TenantCreateRequest request = TenantCreateRequest.builder()
                .name("New Corp")
                .status(TenantStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/management/tenants")
                        .cookie(accessCookie(platformAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New Corp"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Creating a duplicate tenant fails")
    void createDuplicateTenantFails() throws Exception {
        TenantCreateRequest request = TenantCreateRequest.builder()
                .name("Acme Corp")
                .status(TenantStatus.ACTIVE)
                .build();

        mockMvc.perform(post("/api/management/tenants")
                        .cookie(accessCookie(platformAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tenant already exists"));
    }

    @Test
    @DisplayName("Platform admin can update a tenant")
    void platformAdminCanUpdateTenant() throws Exception {
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .name("Acme Updated")
                .status(TenantStatus.INACTIVE)
                .build();

        mockMvc.perform(put("/api/management/tenants/{id}", tenantId)
                        .cookie(accessCookie(platformAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Acme Updated"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("Platform admin can delete a tenant")
    void platformAdminCanDeleteTenant() throws Exception {
        Tenant toDelete = tenantRepository.save(
                Tenant.builder().name("To Delete").status(TenantStatus.ACTIVE).build());

        mockMvc.perform(delete("/api/management/tenants/{id}", toDelete.getId())
                        .cookie(accessCookie(platformAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tenant deleted successfully"));
    }

    @Test
    @DisplayName("Request without name parameter returns all tenants")
    void requestWithoutNameReturnsAll() throws Exception {
        tenantRepository.save(Tenant.builder().name("Another Corp").status(TenantStatus.ACTIVE).build());

        mockMvc.perform(get("/api/management/tenants")
                        .cookie(accessCookie(platformAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    private User createUser(String username, String email, com.example.demo.entity.Role role, Tenant tenant) {
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
        user.setRoles(new HashSet<>());
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
}
