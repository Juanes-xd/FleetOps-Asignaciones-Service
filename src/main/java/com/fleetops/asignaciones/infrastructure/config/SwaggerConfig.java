package com.fleetops.asignaciones.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI fleetOpsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FleetOps — Microservicio de Asignaciones")
                        .description("API REST para gestión de asignaciones de vehículos y conductores " +
                                     "mediante arquitectura hexagonal y patrón SAGA orquestado.")
                        .version("1.0.0"));
    }
}
