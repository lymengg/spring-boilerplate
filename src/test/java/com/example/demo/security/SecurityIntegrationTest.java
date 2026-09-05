package com.example.demo.security;

import com.example.demo.config.JwtConfig;
import com.example.demo.dto.LoginRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.cookie.AuthCookieService;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.security.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    private Cookie accessCookie(String token) {
        return new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, token);
    }

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid access token cookie returns 401")
    void invalidTokenCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .cookie(accessCookie("invalid-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Expired access token cookie returns 401")
    void expiredTokenCookieReturns401() throws Exception {
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
                        .cookie(accessCookie(expiredToken)))
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
                        .cookie(accessCookie(validToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("State-changing request from a non-allowed origin is blocked (CSRF)")
    void crossOriginStateChangingRequestBlocked() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("State-changing request from the allowed frontend origin passes the origin check")
    void sameOriginStateChangingRequestAllowed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()); // passes CSRF check, fails validation
    }

    @Test
    @DisplayName("Safe methods are exempt from the origin check (but still require auth)")
    void safeMethodsExemptFromOriginCheck() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized()); // origin check skipped for GET; no session → 401
    }

    @Test
    @DisplayName("CSP violation reports are accepted without a session")
    void cspReportAcceptedWithoutSession() throws Exception {
        mockMvc.perform(post("/api/csp-report")
                        .header("Origin", "http://localhost:3000")
                        .contentType("application/csp-report")
                        .content("{\"csp-report\":{\"violated-directive\":\"script-src\"}}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("API responses carry restrictive security headers (CSP default-src 'none', X-Frame-Options DENY)")
    void apiResponsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/me").secure(true))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }
}
