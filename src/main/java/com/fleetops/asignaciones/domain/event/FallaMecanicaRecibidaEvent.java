package com.fleetops.asignaciones.domain.event;

import java.util.Date;
import java.util.UUID;

/**
 * vehicle_id llega como la placa del vehículo (no su id interno): Incidentes solo conoce
 * la placa. La resolución a idVehiculo ocurre en la capa de aplicación antes de usarlo
 * para buscar la asignación afectada.
 */
public record FallaMecanicaRecibidaEvent(
        String incident_id,
        String vehicle_id,
        String description,
        UUID driver_id,
        String incident_type,
        String severity,
        Date event_date
) {}
