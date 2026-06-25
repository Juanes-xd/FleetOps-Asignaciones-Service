package com.fleetops.asignaciones.infrastructure.messaging.publisher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventPublisherAdapter")
class KafkaEventPublisherAdapterTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaEventPublisherAdapter publisher;

    @Test
    @DisplayName("publicar: envia mensaje correctamente")
    void publicar_enviaMensajeCorrectamente() {

        CompletableFuture<SendResult<String, Object>> future =
                new CompletableFuture<>();

        future.complete(mock(SendResult.class));

        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(future);

        publisher.publicar("topic.test", "evento");

        verify(kafkaTemplate).send("topic.test", "evento");
    }

    @Test
    @DisplayName("publicar: error al enviar mensaje")
    void publicar_errorAlEnviarMensaje() {

        CompletableFuture<SendResult<String, Object>> future =
                new CompletableFuture<>();

        future.completeExceptionally(
                new RuntimeException("Kafka error")
        );

        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(future);

        publisher.publicar("topic.test", "evento");

        verify(kafkaTemplate).send("topic.test", "evento");
    }
}