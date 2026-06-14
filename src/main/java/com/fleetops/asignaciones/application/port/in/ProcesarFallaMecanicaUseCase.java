package com.fleetops.asignaciones.application.port.in;

import com.fleetops.asignaciones.domain.event.FallaMecanicaRecibidaEvent;

public interface ProcesarFallaMecanicaUseCase {
    void procesar(FallaMecanicaRecibidaEvent evento);
}
