package com.fleetops.asignaciones.domain.event;

import java.util.UUID;

public record AsignacionCompletadaEvent(
        UUID idSaga,
        UUID idAsignacion,
        UUID idVehiculo,
        UUID idConductor
) {}
