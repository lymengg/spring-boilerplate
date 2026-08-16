package com.example.demo.controller;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.constants.UserPermission;
import com.example.demo.repository.RoleRepository;
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
class RoleManagementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private String adminToken;
    private String managerToken;
    private Long customRoleId;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        Role managerRole = roleRepository.findByName("USER_MANAGER").orElseThrow();

        User admin = createUser("adminuser", "admin@example.com", adminRole);
        User manager = createUser("manager", "manager@example.com", managerRole);

        adminToken = generateToken(admin.getUsername());
        managerToken = generateToken(manager.getUsername());

        Role custom = Role.builder()
                .name("CUSTOM")
                .description("Custom role")
                .build();
        custom = roleRepository.save(custom);
        customRoleId = custom.getId();
    }

    private User createUser(String username, String email, Role role) {
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
    @DisplayName("Admin can list roles")
    void adminCanListRoles() throws Exception {
        mockMvc.perform(get("/api/management/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("User manager cannot list roles")
    void userManagerCannotListRoles() throws Exception {
        mockMvc.perform(get("/api/management/roles")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Admin can create role")
    void adminCanCreateRole() throws Exception {
        mockMvc.perform(post("/api/management/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "REPORTER", "description", "Reporter"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("REPORTER"));
    }

    @Test
    @DisplayName("Admin cannot update built-in role")
    void adminCannotUpdateBuiltInRole() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        mockMvc.perform(put("/api/management/roles/{id}", userRole.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "USER", "description", "Updated"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin can add permission to custom role")
    void adminCanAddPermission() throws Exception {
        mockMvc.perform(post("/api/management/roles/{id}/permissions", customRoleId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("permission", "USER_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    @DisplayName("Admin can remove permission from custom role")
    void adminCanRemovePermission() throws Exception {
        Role role = roleRepository.findById(customRoleId).orElseThrow();
        role.getPermissions().add(UserPermission.USER_READ);
        roleRepository.save(role);

        mockMvc.perform(delete("/api/management/roles/{id}/permissions", customRoleId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("permission", "USER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin cannot delete built-in role")
    void adminCannotDeleteBuiltInRole() throws Exception {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        mockMvc.perform(delete("/api/management/roles/{id}", userRole.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("User manager cannot create role")
    void userManagerCannotCreateRole() throws Exception {
        mockMvc.perform(post("/api/management/roles")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "HACKER", "description", "Hacker"))))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Admin cannot delete role that is assigned to users")
    void adminCannotDeleteRoleInUse() throws Exception {
        User user = createUser("roletest", "roletest@example.com", roleRepository.findByName("USER").orElseThrow());
        Role custom = roleRepository.findById(customRoleId).orElseThrow();
        user.getRoles().add(custom);
        userRepository.save(user);

        mockMvc.perform(delete("/api/management/roles/{id}", customRoleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin cannot create role with existing name in different case")
    void adminCannotCreateRoleWithExistingName() throws Exception {
        mockMvc.perform(post("/api/management/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "user", "description", "Duplicate"))))
                .andExpect(status().is(400));
    }

    @Test
    @DisplayName("Admin cannot update role to an existing name")
    void adminCannotUpdateRoleToExistingName() throws Exception {
        mockMvc.perform(put("/api/management/roles/{id}", customRoleId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "USER", "description", "Collision"))))
                .andExpect(status().is(400));
    }
}
