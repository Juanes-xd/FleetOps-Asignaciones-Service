package com.fleetops.asignaciones.infrastructure.messaging.dto;

import java.util.UUID;

/**
 * Mensaje que llega desde el microservicio de Vehículos
 * cuando no puede asignar un vehículo (sin stock, tipo no disponible, etc).
 *
 * topic: fleetops.asignaciones.vehiculo.fallido
 *
 * Asignaciones reacciona ejecutando la compensación.
 */
public record VehiculoRechazadoMessage(
        UUID idAsignacion,
        String motivo
) {}
