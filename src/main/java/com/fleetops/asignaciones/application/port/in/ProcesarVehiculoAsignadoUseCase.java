package com.fleetops.asignaciones.application.port.in;

import java.util.UUID;

/**
 * Puerto de entrada activado cuando Vehículos confirma
 * que asignó un vehículo (evento VehiculoAsignadoEvent recibido).
 * En coreografía este es el mecanismo de avance del flujo:
 * Asignaciones reacciona al evento sin necesidad de orquestador.
 */
public interface ProcesarVehiculoAsignadoUseCase {
    void procesar(UUID idAsignacion, UUID idVehiculo);
}
