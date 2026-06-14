package com.fleetops.asignaciones.domain.event;

import java.util.UUID;

public record FallaMecanicaRecibidaEvent(
        UUID idIncidente,
        UUID idVehiculo,
        UUID idAsignacion,
        String descripcion
) {}
