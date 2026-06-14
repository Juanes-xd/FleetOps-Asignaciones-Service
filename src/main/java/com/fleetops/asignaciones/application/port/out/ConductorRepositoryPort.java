package com.fleetops.asignaciones.application.port.out;

import com.fleetops.asignaciones.domain.model.Conductor;
import java.util.Optional;
import java.util.UUID;

public interface ConductorRepositoryPort {
    Optional<Conductor> buscarDisponiblePorTipoVehiculo(String tipoVehiculo);
    Conductor guardar(Conductor conductor);
    Optional<Conductor> buscarPorId(UUID id);
}
