package com.fleetops.asignaciones.application.port.out;

import com.fleetops.asignaciones.domain.model.SagaRegistro;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para persistencia de SAGAs.
 * En coreografía sin outbox no se necesita buscarPendientesPorAccion —
 * el flujo avanza por eventos Kafka, no por polling.
 */
public interface SagaRepositoryPort {
    SagaRegistro guardar(SagaRegistro saga);
    Optional<SagaRegistro> buscarPorId(UUID id);
    Optional<SagaRegistro> buscarPorAsignacionId(UUID idAsignacion);
}
