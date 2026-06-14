package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.AsignacionCompletadaEvent;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.domain.model.Conductor;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehiculoAsignadoService — reacción coreografiada a confirmación de Vehículos")
class VehiculoAsignadoServiceTest {

    @Mock AsignacionRepositoryPort asignacionRepository;
    @Mock SagaRepositoryPort sagaRepository;
    @Mock EventPublisherPort eventPublisher;

    @InjectMocks VehiculoAsignadoService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicAsignacionCompletada",
                "fleetops.asignaciones.completada");
    }

    @Test
    @DisplayName("procesar: dado vehiculo confirmado, completa asignacion, SAGA y publica evento")
    void procesar_dadoVehiculoConfirmado_completaTodoYPublica() {
        // Arrange
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo   = UUID.randomUUID();

        Conductor conductor = Conductor.builder().id(UUID.randomUUID()).build();
        Asignacion asignacion = Asignacion.builder()
                .id(idAsignacion)
                .conductor(conductor)
                .build();
        SagaRegistro saga = SagaRegistro.builder()
                .id(UUID.randomUUID())
                .asignacion(asignacion)
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.of(asignacion));
        when(sagaRepository.buscarPorAsignacionId(idAsignacion)).thenReturn(Optional.of(saga));
        when(asignacionRepository.guardar(any())).thenReturn(asignacion);
        when(sagaRepository.guardar(any())).thenReturn(saga);

        // Act
        service.procesar(idAsignacion, idVehiculo);

        // Assert — asignación actualizada
        assertThat(asignacion.getVehiculoId()).isEqualTo(idVehiculo);

        // Assert — SAGA completada
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.COMPLETADO);
        assertThat(saga.getVehiculoId()).isEqualTo(idVehiculo);

        // Assert — evento publicado
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq("fleetops.asignaciones.completada"), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(AsignacionCompletadaEvent.class);
    }

    @Test
    @DisplayName("procesar: dado asignacion inexistente, lanza NoSuchElementException")
    void procesar_dadoAsignacionInexistente_lanzaExcepcion() {
        // Arrange
        UUID idAsignacion = UUID.randomUUID();
        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.procesar(idAsignacion, UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(idAsignacion.toString());

        verify(eventPublisher, never()).publicar(any(), any());
    }
}
