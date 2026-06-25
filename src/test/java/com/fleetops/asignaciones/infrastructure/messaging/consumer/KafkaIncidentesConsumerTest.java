package com.fleetops.asignaciones.infrastructure.messaging.consumer;

import com.fleetops.asignaciones.application.port.in.ProcesarFallaMecanicaUseCase;
import com.fleetops.asignaciones.infrastructure.messaging.dto.FallaMecanicaMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaIncidentesConsumer")
class KafkaIncidentesConsumerTest {

    @Mock
    private ProcesarFallaMecanicaUseCase procesarFallaMecanicaUseCase;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private KafkaIncidentesConsumer consumer;

    @Test
    @DisplayName("onFallaMecanica: procesa correctamente y hace ACK")
    void onFallaMecanica_procesaCorrectamente_yHaceAck() {

        FallaMecanicaMessage mensaje = new FallaMecanicaMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Falla en el motor"
        );

        consumer.onFallaMecanica(mensaje, ack);

        verify(procesarFallaMecanicaUseCase).procesar(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onFallaMecanica: si ocurre error NO hace ACK")
    void onFallaMecanica_siHayError_noHaceAck() {

        FallaMecanicaMessage mensaje = new FallaMecanicaMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Falla en el motor"
        );

        doThrow(new RuntimeException("Error procesando incidente"))
                .when(procesarFallaMecanicaUseCase)
                .procesar(any());

        consumer.onFallaMecanica(mensaje, ack);

        verify(ack, never()).acknowledge();
    }

}
