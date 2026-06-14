package com.fleetops.asignaciones.infrastructure.persistence;

import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.infrastructure.persistence.jpa.AsignacionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AsignacionPersistenceAdapter implements AsignacionRepositoryPort {

    private final AsignacionJpaRepository jpaRepository;

    @Override
    public Asignacion guardar(Asignacion asignacion) {
        return jpaRepository.save(asignacion);
    }

    @Override
    public Optional<Asignacion> buscarPorId(UUID id) {
        return jpaRepository.findById(id);
    }
}
