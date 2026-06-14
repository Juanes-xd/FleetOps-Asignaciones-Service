package com.fleetops.asignaciones.infrastructure.messaging.publisher;

import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Driven adapter — implementa EventPublisherPort usando Kafka.
 *
 * Sin OutboxWorker, la publicación ocurre en el hilo del servicio
 * dentro de la transacción activa. KafkaTemplate está configurado
 * con un producer transaccional (EXACTLY_ONCE) en KafkaConfig,
 * lo que garantiza que si la transacción de DB hace rollback,
 * el mensaje Kafka también se descarta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publicar(String topic, Object evento) {
        kafkaTemplate.send(topic, evento)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error publicando en topic {}: {}", topic, ex.getMessage());
                        throw new RuntimeException(
                                "Fallo al publicar evento en Kafka topic: " + topic, ex);
                    }
                    log.info("Evento publicado en topic {} offset {}",
                            topic, result.getRecordMetadata().offset());
                });
    }
}
