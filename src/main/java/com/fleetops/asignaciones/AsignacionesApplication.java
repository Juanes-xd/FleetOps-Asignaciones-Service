package com.fleetops.asignaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio de Asignaciones.
 * @EnableScheduling eliminado — no hay OutboxWorker en el modelo coreografiado.
 */
@SpringBootApplication
public class AsignacionesApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsignacionesApplication.class, args);
    }
}
