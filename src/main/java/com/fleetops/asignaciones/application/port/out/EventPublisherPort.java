package com.fleetops.asignaciones.application.port.out;

public interface EventPublisherPort {
    void publicar(String topic, Object evento);
}
