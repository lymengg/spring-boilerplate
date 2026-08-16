package com.example.demo.security;

import com.example.demo.config.JwtConfig;
import com.example.demo.dto.LoginRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

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
    private JwtConfig jwtConfig;

    @Autowired
    private ObjectMapper objectMapper;

    private String validToken;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        user.getRoles().add(employeeRole);
        userRepository.save(user);

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        validToken = jwtTokenProvider.generateAccessToken(authentication);
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid token format returns 401")
    void invalidTokenFormatReturns401() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Malformed authorization header returns 401")
    void malformedAuthorizationHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", validToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Expired token returns 401")
    void expiredTokenReturns401() throws Exception {
        Date now = Date.from(Instant.now().minus(2, ChronoUnit.HOURS));
        Date expiry = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));

        String expiredToken = Jwts.builder()
                .subject("testuser")
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(now)
                .expiration(expiry)
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .signWith(Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();

        mockMvc.perform(get("/api/expenses")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Missing required fields in request body returns 400")
    void missingRequestFieldsReturn400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .usernameOrEmail("")
                .password("")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Self-registration endpoint is no longer publicly accessible")
    void registerEndpointIsNoLongerPublic() throws Exception {
        String body = "{\"username\":\"attacker\",\"email\":\"attacker@example.com\",\"password\":\"SecurePass123!\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("User without permission receives 403 for protected management endpoint")
    void unauthorizedRoleReceives403() throws Exception {
        mockMvc.perform(get("/api/management/users")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden());
    }
}
