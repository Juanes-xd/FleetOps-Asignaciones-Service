package com.fleetops.asignaciones.infrastructure.web.dto;

import java.util.UUID;

public record AsignacionResponse(
        UUID idSaga,
        UUID idAsignacion,
        String mensaje
) {}
