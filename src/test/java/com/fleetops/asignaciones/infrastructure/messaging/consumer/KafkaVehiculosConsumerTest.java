package com.fleetops.asignaciones.infrastructure.messaging.consumer;

import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoAsignadoUseCase;
import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoRechazadoUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaVehiculosConsumer — driving adapter coreografiado")
class KafkaVehiculosConsumerTest {

    @Mock ProcesarVehiculoAsignadoUseCase procesarVehiculoAsignadoUseCase;
    @Mock ProcesarVehiculoRechazadoUseCase procesarVehiculoRechazadoUseCase;
    @Mock Acknowledgment ack;

    @InjectMocks KafkaVehiculosConsumer consumer;

    @Test
    @DisplayName("onVehiculoConfirmado: dado mensaje de confirmacion, delega en use case y hace ACK")
    void onVehiculoConfirmado_delegaEnUseCaseYHaceAck() {
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo   = UUID.randomUUID();
        Map<String, Object> mensaje = Map.of(
                "idAsignacion", idAsignacion.toString(),
                "idVehiculo", idVehiculo.toString()
        );

        consumer.onVehiculoConfirmado(mensaje, ack);

        verify(procesarVehiculoAsignadoUseCase).procesar(idAsignacion, idVehiculo);
        verify(ack).acknowledge();
        verifyNoInteractions(procesarVehiculoRechazadoUseCase);
    }

    @Test
    @DisplayName("onVehiculoConfirmado: dado error en use case, NO hace ACK para que Kafka reintente")
    void onVehiculoConfirmado_dadoError_noHaceAck() {
        Map<String, Object> mensaje = Map.of(
                "idAsignacion", UUID.randomUUID().toString(),
                "idVehiculo", UUID.randomUUID().toString()
        );
        doThrow(new RuntimeException("DB no disponible"))
                .when(procesarVehiculoAsignadoUseCase).procesar(any(), any());

        consumer.onVehiculoConfirmado(mensaje, ack);

        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("onVehiculoRechazado: dado mensaje de rechazo, delega en use case de compensacion y hace ACK")
    void onVehiculoRechazado_delegaEnUseCaseCompensacionYHaceAck() {
        UUID idAsignacion = UUID.randomUUID();
        String motivo     = "Sin stock de vehiculos CAMION";
        Map<String, Object> mensaje = Map.of(
                "idAsignacion", idAsignacion.toString(),
                "motivo", motivo
        );

        consumer.onVehiculoRechazado(mensaje, ack);

        verify(procesarVehiculoRechazadoUseCase).procesar(idAsignacion, motivo);
        verify(ack).acknowledge();
        verifyNoInteractions(procesarVehiculoAsignadoUseCase);
    }

    @Test
    @DisplayName("onVehiculoRechazado: dado error en compensacion, NO hace ACK")
    void onVehiculoRechazado_dadoError_noHaceAck() {
        Map<String, Object> mensaje = Map.of(
                "idAsignacion", UUID.randomUUID().toString(),
                "motivo", "motivo"
        );
        doThrow(new RuntimeException("Error de compensacion"))
                .when(procesarVehiculoRechazadoUseCase).procesar(any(), any());

        consumer.onVehiculoRechazado(mensaje, ack);

        verify(ack, never()).acknowledge();
    }
}
