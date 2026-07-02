package com.fleetops.asignaciones.infrastructure.web.dto;


import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CrearAsignacionRequest(

        @NotBlank(message = "El tipo de vehículo es obligatorio")
        String tipoVehiculo,

        @NotNull(message = "La fecha de inicio es obligatoria")
        @Future(message = "La fecha de inicio debe ser futura")
        LocalDate fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        @Future(message = "La fecha de fin debe ser futura")
        LocalDate fechaFin,

        @NotNull(message = "Los kilometros a recorrer son obligatorios")
        Integer kilometros
) {}
