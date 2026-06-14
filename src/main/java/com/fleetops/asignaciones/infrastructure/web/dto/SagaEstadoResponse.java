package com.fleetops.asignaciones.infrastructure.web.dto;

import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import java.util.UUID;

public record SagaEstadoResponse(
        UUID idSaga,
        EstadoSaga estado,
        UUID vehiculoId,
        String motivoFallo
) {}
