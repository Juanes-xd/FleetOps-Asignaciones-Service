package com.fleetops.asignaciones.infrastructure.messaging.dto;

import java.util.UUID;

/**
 * Mensaje que llega desde el microservicio de Vehículos
 * cuando confirma que asignó un vehículo exitosamente.
 *
 * topic: fleetops.asignaciones.vehiculo.confirmado
 *
 * En coreografía, Vehículos publica este evento de forma autónoma
 * tras procesar el VehiculoSolicitadoEvent. No hay orquestador
 * que le diga cuándo publicarlo.
 */
public record VehiculoConfirmadoMessage(
        String idAsignacion,
        String idVehiculo
) {}
