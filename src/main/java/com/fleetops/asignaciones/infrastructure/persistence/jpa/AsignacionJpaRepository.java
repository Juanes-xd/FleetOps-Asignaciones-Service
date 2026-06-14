package com.fleetops.asignaciones.infrastructure.persistence.jpa;

import com.fleetops.asignaciones.domain.model.Asignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AsignacionJpaRepository extends JpaRepository<Asignacion, UUID> {}
