package com.fleetops.asignaciones.infrastructure.web.controller;

import com.fleetops.asignaciones.application.port.in.CrearAsignacionUseCase;
import com.fleetops.asignaciones.infrastructure.web.dto.AsignacionResponse;
import com.fleetops.asignaciones.infrastructure.web.dto.CrearAsignacionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/asignaciones")
@RequiredArgsConstructor
@Tag(name = "Asignaciones", description = "Gestión de asignaciones de vehículos y conductores")
public class AsignacionController {

    private final CrearAsignacionUseCase crearAsignacionUseCase;

    @PostMapping
    @Operation(summary = "Crear asignación",
               description = "Inicia el proceso SAGA de asignación. Retorna 202 Accepted " +
                             "con el id de la saga para seguimiento asíncrono.")
    public ResponseEntity<AsignacionResponse> crear(
            @Valid @RequestBody CrearAsignacionRequest request) {

        CrearAsignacionUseCase.Command command = new CrearAsignacionUseCase.Command(
                request.tipoVehiculo(),
                request.fechaInicio(),
                request.fechaFin(),
                request.kilometros()
        );

        CrearAsignacionUseCase.Result result = crearAsignacionUseCase.ejecutar(command);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AsignacionResponse(
                        result.idSaga(),
                        result.idAsignacion(),
                        "Asignación en proceso. Consulte el estado con el id de saga."
                ));
    }
}
