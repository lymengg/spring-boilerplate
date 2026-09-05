package com.example.demo.config;

import com.example.demo.constants.Roles;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Value("${seed.admin.username:admin}")
    private String adminUsername;

    @Value("${seed.admin.email:admin@example.com}")
    private String adminEmail;

    @Value("${seed.admin.password:admin123}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Seed admin user '{}' already exists — skipping creation", adminUsername);
            return;
        }

        Role platformAdminRole = roleRepository.findByName(Roles.PLATFORM_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "PLATFORM_ADMIN role not found. Ensure Flyway migrations have run."));

        User admin = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .firstName("Super")
                .lastName("Admin")
                .enabled(true)
                .roles(Set.of(platformAdminRole))
                .build();

        userRepository.save(admin);
        log.warn("Seed admin user created — username='{}', email='{}'", adminUsername, adminEmail);
    }
}
