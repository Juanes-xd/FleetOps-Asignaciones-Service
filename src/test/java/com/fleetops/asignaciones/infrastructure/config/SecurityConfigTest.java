package com.fleetops.asignaciones.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void jwtDecoder_debeCrearseCorrectamente() {

        SecurityConfig config = new SecurityConfig();

        ReflectionTestUtils.setField(
                config,
                "jwtSecret",
                "12345678901234567890123456789012"
        );

        JwtDecoder decoder = config.jwtDecoder();

        assertThat(decoder).isNotNull();
    }
}