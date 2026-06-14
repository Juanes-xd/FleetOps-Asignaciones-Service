package com.fleetops.asignaciones.infrastructure.web.controller;

import com.fleetops.asignaciones.application.port.in.ConsultarSagaUseCase;
import com.fleetops.asignaciones.infrastructure.web.dto.SagaEstadoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/asignaciones/saga")
@RequiredArgsConstructor
@Tag(name = "SAGA", description = "Consulta del estado de procesos SAGA de asignación")
public class SagaController {

    private final ConsultarSagaUseCase consultarSagaUseCase;

    @GetMapping("/{idSaga}")
    @Operation(summary = "Consultar estado de SAGA",
               description = "Retorna el estado actual de la saga: " +
                             "PENDIENTE_CONFIRMACION_VEHICULO, COMPLETADO o FALLIDO.")
    public ResponseEntity<SagaEstadoResponse> consultar(@PathVariable UUID idSaga) {
        ConsultarSagaUseCase.Result result = consultarSagaUseCase.consultar(idSaga);
        return ResponseEntity.ok(new SagaEstadoResponse(
                result.idSaga(),
                result.estado(),
                result.vehiculoId(),
                result.motivoFallo()
        ));
    }
}
