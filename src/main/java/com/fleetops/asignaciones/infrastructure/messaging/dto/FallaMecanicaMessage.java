package com.fleetops.asignaciones.infrastructure.messaging.dto;

import java.util.UUID;

/**
 * Mensaje que llega desde el microservicio de Incidentes
 * via topic: fleetops.incidentes.falla.mecanica
 */
public record FallaMecanicaMessage(
        UUID idIncidente,
        UUID idVehiculo,
        UUID idAsignacion,
        String descripcion
) {}
