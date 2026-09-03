package com.example.demo.config;

import com.example.demo.dto.ApiResponse;
import com.example.demo.security.filter.JwtAuthenticationFilter;
import com.example.demo.security.filter.OriginCheckFilter;
import com.example.demo.security.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OriginCheckFilter originCheckFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/mfa/verify", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/csp-report").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                 .authenticationProvider(authenticationProvider())
                 // Order matters: the JWT filter's position must be registered
                 // before the origin filter can reference it as its anchor.
                 .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                 // CSRF: Origin validation for cookie-based browser flows (see OriginCheckFilter).
                 .addFilterBefore(originCheckFilter, JwtAuthenticationFilter.class)
                 .headers(headers -> headers
                         // The API renders no HTML: deny framing outright and allow
                         // nothing by default (defends against content-sniffing of
                         // JSON/error bodies as HTML).
                         .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                         .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                         .contentTypeOptions(contentType -> {})
                         .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                         .httpStrictTransportSecurity(hsts -> hsts
                                 .maxAgeInSeconds(31536000)
                                 .includeSubDomains(true))
                         .addHeaderWriter((request, response) -> {
                             response.setHeader("Permissions-Policy",
                                     "geolocation=(), microphone=(), camera=(), payment=(), usb=()");
                         })
                 )
                 .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write(objectMapper
                                    .writeValueAsString(ApiResponse.error("Invalid credentials")));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write(objectMapper
                                    .writeValueAsString(ApiResponse.error("Access denied: Insufficient permissions")));
                        })
                 )
                .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
