package com.fleetops.asignaciones.domain.event;

import java.util.UUID;

public record AsignacionFallidaEvent(
        UUID idSaga,
        UUID idAsignacion,
        String motivo
) {}
