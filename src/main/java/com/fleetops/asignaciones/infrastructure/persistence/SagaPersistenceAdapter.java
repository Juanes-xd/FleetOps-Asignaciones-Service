package com.fleetops.asignaciones.infrastructure.persistence;

import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import com.fleetops.asignaciones.infrastructure.persistence.jpa.SagaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaPersistenceAdapter implements SagaRepositoryPort {

    private final SagaJpaRepository jpaRepository;

    @Override
    public SagaRegistro guardar(SagaRegistro saga) {
        return jpaRepository.save(saga);
    }

    @Override
    public Optional<SagaRegistro> buscarPorId(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<SagaRegistro> buscarPorAsignacionId(UUID idAsignacion) {
        return jpaRepository.findByAsignacionId(idAsignacion);
    }
}
