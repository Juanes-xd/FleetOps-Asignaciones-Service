package com.fleetops.asignaciones.infrastructure.messaging.consumer;

import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoAsignadoUseCase;
import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoRechazadoUseCase;
import com.fleetops.asignaciones.infrastructure.messaging.dto.VehiculoConfirmadoMessage;
import com.fleetops.asignaciones.infrastructure.messaging.dto.VehiculoRechazadoMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Driving adapter — escucha las respuestas del microservicio de Vehículos.
 *
 * En el modelo coreografiado NO hay un orquestador que interprete
 * las respuestas. Este consumer simplemente recibe el evento y
 * delega en el use case correspondiente, que contiene la lógica.
 *
 * Dos topics separados en lugar de un campo booleano "exitoso":
 * - confirmado → ProcesarVehiculoAsignadoUseCase
 * - fallido    → ProcesarVehiculoRechazadoUseCase
 *
 * ACK manual: el offset solo avanza si el procesamiento fue exitoso.
 * Si falla, Kafka reintentará según la política del consumer group.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaVehiculosConsumer {

    private final ProcesarVehiculoAsignadoUseCase procesarVehiculoAsignadoUseCase;
    private final ProcesarVehiculoRechazadoUseCase procesarVehiculoRechazadoUseCase;

    @KafkaListener(
            topics = "${asignaciones.kafka.topics.vehiculos-confirmado}",
            groupId = "${asignaciones.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onVehiculoConfirmado(Map<String, Object> payload, Acknowledgment ack) {
        try {
            // Convertir manualmente
            String idAsignacion = (String) payload.get("idAsignacion");
            String idVehiculo = (String) payload.get("idVehiculo");

            log.info("Coreografia: vehiculo {} confirmado para asignacion {}",
                    idVehiculo, idAsignacion);

            procesarVehiculoAsignadoUseCase.procesar(
                    UUID.fromString(idAsignacion),
                    UUID.fromString(idVehiculo)
            );
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error procesando confirmacion: {}", ex.getMessage(), ex);
        }
    }

    @KafkaListener(
            topics = "${asignaciones.kafka.topics.vehiculos-fallido}",
            groupId = "${asignaciones.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onVehiculoRechazado(Map<String, Object> payload, Acknowledgment ack) {
        try {
            String idAsignacion = (String) payload.get("idAsignacion");
            String motivo = (String) payload.get("motivo");

            log.warn("Coreografia: vehiculo rechazado para asignacion {}. Motivo: {}",
                    idAsignacion, motivo);

            procesarVehiculoRechazadoUseCase.procesar(UUID.fromString(idAsignacion), motivo);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error procesando rechazo: {}", ex.getMessage(), ex);
        }
    }
}