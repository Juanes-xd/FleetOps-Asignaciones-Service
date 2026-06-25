package com.fleetops.asignaciones.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void fleetOpsOpenAPI_debeCrearOpenAPI() {

        SwaggerConfig config = new SwaggerConfig();

        OpenAPI api = config.fleetOpsOpenAPI();

        assertThat(api).isNotNull();
        assertThat(api.getInfo()).isNotNull();
        assertThat(api.getInfo().getTitle())
                .contains("FleetOps");
        assertThat(api.getInfo().getVersion())
                .isEqualTo("1.0.0");
    }
}