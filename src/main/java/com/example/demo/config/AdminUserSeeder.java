package com.example.demo.config;

import com.example.demo.constants.Roles;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.service.RoleManagementService;
import com.example.demo.service.UserService;
import com.example.demo.validation.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Seeds the platform admin (PLATFORM_ADMIN, no tenant) on first startup when
 * SEED_ADMIN_PASSWORD is set. Idempotent: skips if the user already exists.
 */
@Component
@Slf4j
public class AdminUserSeeder implements ApplicationRunner {

    private final UserService userService;
    private final RoleManagementService roleManagementService;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    @Autowired
    public AdminUserSeeder(
            UserService userService,
            RoleManagementService roleManagementService,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed-admin.username:admin}") String username,
            @Value("${app.seed-admin.email:admin@example.com}") String email,
            @Value("${app.seed-admin.password:}") String password) {
        this.userService = userService;
        this.roleManagementService = roleManagementService;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(password)) {
            log.info("SEED_ADMIN_PASSWORD not set — skipping admin user seeding");
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            throw new IllegalStateException("Admin seeding requires SEED_ADMIN_USERNAME and SEED_ADMIN_EMAIL");
        }
        if (!PasswordValidator.PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalStateException("SEED_ADMIN_PASSWORD must be at least 8 characters and include "
                    + "uppercase, lowercase, a digit and a special character");
        }
        if (userService.existsByUsername(username) || userService.existsByEmail(email)) {
            log.info("Admin user '{}' already exists — skipping seeding", username);
            return;
        }
        Role adminRole = roleManagementService.findByName(Roles.PLATFORM_ADMIN);
        User admin = User.builder()
                .username(username.trim())
                .email(email.trim().toLowerCase())
                .password(passwordEncoder.encode(password))
                .firstName("Platform")
                .lastName("Admin")
                .build();
        admin.getRoles().add(adminRole);
        userService.save(admin);
        log.info("Seeded admin user '{}' with role {}", username, Roles.PLATFORM_ADMIN);
    }
}
