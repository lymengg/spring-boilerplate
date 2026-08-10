package com.example.demo.controller;

import com.example.demo.entity.Role;
import com.example.demo.entity.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteControllerIntegrationTest {

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

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

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
        user.getRoles().add(userRole);
        user = userRepository.save(user);

        User admin = User.builder()
                .username("adminuser")
                .email("admin@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Admin")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        admin.getRoles().add(adminRole);
        admin = userRepository.save(admin);

        userToken = generateToken(user.getUsername());
        adminToken = generateToken(admin.getUsername());
    }

    private String generateToken(String username) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        return jwtTokenProvider.generateAccessToken(authentication);
    }

    @Test
    @DisplayName("Should create note for authenticated user")
    void shouldCreateNote() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("title", "My note", "content", "Hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("My note"))
                .andExpect(jsonPath("$.data.ownerUsername").value("testuser"));
    }

    @Test
    @DisplayName("Should reject unauthenticated access")
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("title", "My note", "content", "Hello"))))
                .andExpect(status().is(401));
    }

    @Test
    @DisplayName("Should get own notes only")
    void shouldGetOwnNotesOnly() throws Exception {
        createNote("Owner note", "content");

        mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("Should deny user access to admin endpoint")
    void shouldDenyUserAccessToAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/notes/all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("Should allow admin to access all notes")
    void shouldAllowAdminToAccessAllNotes() throws Exception {
        createNote("Owner note", "content");

        mockMvc.perform(get("/api/notes/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("Should prevent cross-user note access")
    void shouldPreventCrossUserNoteAccess() throws Exception {
        Long noteId = extractNoteId(createNote("Owner note", "content"));

        User other = User.builder()
                .username("otheruser")
                .email("other@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Other")
                .lastName("User")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        other.getRoles().add(roleRepository.findByName("USER").orElseThrow());
        other = userRepository.save(other);
        String otherToken = generateToken(other.getUsername());

        mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().is(400));
    }

    private String createNote(String title, String content) throws Exception {
        return mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("title", title, "content", content))))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Long extractNoteId(String jsonResponse) {
        try {
            var map = objectMapper.readValue(jsonResponse, java.util.HashMap.class);
            var data = (java.util.Map<String, Object>) map.get("data");
            return ((Number) data.get("id")).longValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract note id", e);
        }
    }
}
