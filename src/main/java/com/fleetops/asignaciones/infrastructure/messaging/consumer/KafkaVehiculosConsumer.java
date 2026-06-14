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
    public void onVehiculoConfirmado(VehiculoConfirmadoMessage mensaje, Acknowledgment ack) {
        log.info("Coreografia: vehiculo {} confirmado para asignacion {}",
                mensaje.idVehiculo(), mensaje.idAsignacion());
        try {
            procesarVehiculoAsignadoUseCase.procesar(mensaje.idAsignacion(), mensaje.idVehiculo());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error procesando confirmacion de vehiculo para asignacion {}: {}",
                    mensaje.idAsignacion(), ex.getMessage());
        }
    }

    @KafkaListener(
            topics = "${asignaciones.kafka.topics.vehiculos-fallido}",
            groupId = "${asignaciones.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onVehiculoRechazado(VehiculoRechazadoMessage mensaje, Acknowledgment ack) {
        log.warn("Coreografia: vehiculo rechazado para asignacion {}. Motivo: {}",
                mensaje.idAsignacion(), mensaje.motivo());
        try {
            procesarVehiculoRechazadoUseCase.procesar(mensaje.idAsignacion(), mensaje.motivo());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error procesando rechazo de vehiculo para asignacion {}: {}",
                    mensaje.idAsignacion(), ex.getMessage());
        }
    }
}
