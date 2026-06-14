package com.fleetops.asignaciones.application.port.in;

import java.util.UUID;

/**
 * Puerto de entrada activado cuando Vehículos no puede
 * asignar un vehículo (evento VehiculoNoDisponibleEvent recibido).
 * Inicia la compensación: libera el conductor y publica el evento
 * de liberación de vehículo si ya había sido reservado.
 */
public interface ProcesarVehiculoRechazadoUseCase {
    void procesar(UUID idAsignacion, String motivo);
}
