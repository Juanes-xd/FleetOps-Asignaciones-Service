package com.fleetops.asignaciones.domain.event;

import java.time.LocalDate;
import java.util.UUID;

public record VehiculoSolicitadoEvent(
        UUID idSaga,
        UUID idAsignacion,
        String tipoVehiculo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer kilometros
) {}
