package com.example.demo.controller;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.cookie.AuthCookieManager;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaginationControllerIntegrationTest {

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

    private String adminToken;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        User admin = createUser("admin@example.com", adminRole);
        adminToken = generateToken(admin.getEmail());
    }

    @Test
    @DisplayName("Default page size is 10")
    void defaultPageSizeIsTen() throws Exception {
        mockMvc.perform(get("/api/management/users").cookie(accessCookie(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("Allowed page sizes 10, 25, 50 and 100 are accepted")
    void allowedPageSizesAreAccepted() throws Exception {
        for (int size : new int[]{10, 25, 50, 100}) {
            mockMvc.perform(get("/api/management/users")
                            .param("size", String.valueOf(size))
                            .cookie(accessCookie(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(size));
        }
    }

    @Test
    @DisplayName("A page size outside the allowed set is rejected with 400")
    void disallowedPageSizeIsRejected() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .param("size", "37")
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Page size must be one of: 10, 25, 50, 100"));
    }

    @Test
    @DisplayName("A page size above the 100 limit is rejected with 400")
    void pageSizeAboveLimitIsRejected() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .param("size", "1000")
                        .cookie(accessCookie(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Page size must be one of: 10, 25, 50, 100"));
    }

    private User createUser(String email, Role role) {
        User user = User.builder()
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

    private Cookie accessCookie(String token) {
        return new Cookie(AuthCookieManager.ACCESS_TOKEN_COOKIE, token);
    }
}
