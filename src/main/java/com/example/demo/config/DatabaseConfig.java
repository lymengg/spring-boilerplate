package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
@Getter
@Setter
@Slf4j
public class DatabaseConfig {

    private String url;
    private String username;
    private String password;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(password)) {
            String driverClass = System.getenv("SPRING_DATASOURCE_DRIVER");
            boolean isPostgres = driverClass != null && driverClass.contains("postgresql");
            boolean urlIsPostgres = url != null && url.contains("postgresql");

            if (isPostgres || urlIsPostgres) {
                throw new IllegalStateException(
                    "Database password is required for PostgreSQL. Set via DB_PASSWORD environment variable."
                );
            }

            log.warn("Database password is empty — acceptable for H2 in-memory dev database");
        }
    }
}
