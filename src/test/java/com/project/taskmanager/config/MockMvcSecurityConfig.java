package com.project.taskmanager.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Spring Boot 4 removed the auto-configuration (the old
 * {@code MockMvcSecurityConfiguration}) that applied
 * {@link org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers#springSecurity()}
 * to {@code @AutoConfigureMockMvc}. Without it, {@code @WithMockUser} no longer bridges into the
 * MockMvc request, so every secured endpoint answers 401. Re-apply it via a builder customizer so
 * the existing {@code @WithMockUser} integration tests keep authenticating as before.
 */
@TestConfiguration
public class MockMvcSecurityConfig {

    @Bean
    MockMvcBuilderCustomizer securityMockMvcBuilderCustomizer() {
        return builder -> builder.apply(springSecurity());
    }
}
