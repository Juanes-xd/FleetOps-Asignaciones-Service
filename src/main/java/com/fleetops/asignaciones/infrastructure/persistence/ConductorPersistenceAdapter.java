package com.fleetops.asignaciones.infrastructure.persistence;

import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.model.Conductor;
import com.fleetops.asignaciones.infrastructure.persistence.jpa.ConductorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConductorPersistenceAdapter implements ConductorRepositoryPort {

    private final ConductorJpaRepository jpaRepository;

    @Override
    public Optional<Conductor> buscarDisponiblePorTipoVehiculo(String tipoVehiculo) {
        return jpaRepository.findFirstByTipoVehiculoAndEstado(
                tipoVehiculo, EstadoConductor.DISPONIBLE);
    }

    @Override
    public Conductor guardar(Conductor conductor) {
        return jpaRepository.save(conductor);
    }

    @Override
    public Optional<Conductor> buscarPorId(UUID id) {
        return jpaRepository.findById(id);
    }
}
