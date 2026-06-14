package com.fleetops.asignaciones.infrastructure.persistence.jpa;

import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.model.Conductor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ConductorJpaRepository extends JpaRepository<Conductor, UUID> {
    Optional<Conductor> findFirstByTipoVehiculoAndEstado(String tipoVehiculo, EstadoConductor estado);
}
