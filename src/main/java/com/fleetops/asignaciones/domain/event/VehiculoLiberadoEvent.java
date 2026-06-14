package com.fleetops.asignaciones.domain.event;

import java.util.UUID;

public record VehiculoLiberadoEvent(
        UUID idSaga,
        UUID idVehiculo
) {}
