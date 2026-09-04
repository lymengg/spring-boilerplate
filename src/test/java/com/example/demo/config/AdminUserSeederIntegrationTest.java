package com.example.demo.config;

import com.example.demo.constants.Roles;
import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "app.seed-admin.password=Admin123!",
    "spring.datasource.url=jdbc:h2:mem:seedertestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
class AdminUserSeederIntegrationTest {

    @Autowired
    private UserService userService;

    @Test
    @Transactional
    void seedsPlatformAdminWithoutTenant() {
        User admin = userService.getByUsername("admin");
        assertThat(admin.getTenant()).isNull();
        assertThat(admin.getRoles())
                .extracting(role -> role.getName())
                .contains(Roles.PLATFORM_ADMIN);
    }
}
