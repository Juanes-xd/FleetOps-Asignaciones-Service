package com.fleetops.asignaciones.infrastructure.persistence.jpa;

import com.fleetops.asignaciones.domain.model.SagaRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * En coreografía sin outbox no se necesita buscar por acción pendiente.
 * Solo necesitamos buscar por id y por asignacion_id.
 */
public interface SagaJpaRepository extends JpaRepository<SagaRegistro, UUID> {
    Optional<SagaRegistro> findByAsignacionId(UUID idAsignacion);
}
