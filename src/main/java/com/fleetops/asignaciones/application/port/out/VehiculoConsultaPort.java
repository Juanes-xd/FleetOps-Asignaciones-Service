package com.fleetops.asignaciones.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta al microservicio de Vehículos para resolver la placa reportada por Incidentes
 * al idVehiculo interno que usa este microservicio para identificar la asignación afectada.
 */
public interface VehiculoConsultaPort {
    Optional<UUID> buscarIdPorPlaca(String placa);
}
