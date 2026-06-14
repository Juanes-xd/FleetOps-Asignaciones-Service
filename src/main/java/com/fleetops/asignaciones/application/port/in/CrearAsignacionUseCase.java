package com.fleetops.asignaciones.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

public interface CrearAsignacionUseCase {

    record Command(
            String tipoVehiculo,
            LocalDate fechaInicio,
            LocalDate fechaFin
    ) {}

    record Result(UUID idSaga, UUID idAsignacion) {}

    Result ejecutar(Command command);
}
