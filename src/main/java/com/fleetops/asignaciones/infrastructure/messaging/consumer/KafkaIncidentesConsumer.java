package com.fleetops.asignaciones.infrastructure.messaging.consumer;

import com.fleetops.asignaciones.application.port.in.ProcesarFallaMecanicaUseCase;
import com.fleetops.asignaciones.domain.event.FallaMecanicaRecibidaEvent;
import com.fleetops.asignaciones.infrastructure.messaging.dto.FallaMecanicaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaIncidentesConsumer {

    private final ProcesarFallaMecanicaUseCase procesarFallaMecanicaUseCase;

    /**
     * Driving adapter: escucha fallas mecánicas reportadas por Incidentes.
     * Manual ACK garantiza que el offset solo avanza si el procesamiento fue exitoso.
     */
    @KafkaListener(
            topics = "${asignaciones.kafka.topics.incidentes-falla-mecanica}",
            groupId = "${asignaciones.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFallaMecanica(FallaMecanicaMessage mensaje, Acknowledgment ack) {
        log.info("Evento falla mecanica recibido. Incidente: {} Vehiculo: {}",
                mensaje.idIncidente(), mensaje.idVehiculo());
        try {
            FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                    mensaje.idIncidente(),
                    mensaje.idVehiculo(),
                    mensaje.idAsignacion(),
                    mensaje.descripcion()
            );
            procesarFallaMecanicaUseCase.procesar(evento);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error procesando falla mecanica incidente {}: {}",
                    mensaje.idIncidente(), ex.getMessage());
            // No hacemos ack → Kafka reintentará según la política de reintentos del consumer
        }
    }
}
