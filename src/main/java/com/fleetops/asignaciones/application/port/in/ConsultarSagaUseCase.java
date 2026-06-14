package com.fleetops.asignaciones.application.port.in;

import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import java.util.UUID;

public interface ConsultarSagaUseCase {

    record Result(
            UUID idSaga,
            EstadoSaga estado,
            String motivoFallo,
            UUID vehiculoId
    ) {}

    Result consultar(UUID idSaga);
}
