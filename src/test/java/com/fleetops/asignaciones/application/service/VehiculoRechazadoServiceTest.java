package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.AsignacionFallidaEvent;
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
@DisplayName("VehiculoRechazadoService — compensación coreografiada")
class VehiculoRechazadoServiceTest {

    @Mock AsignacionRepositoryPort asignacionRepository;
    @Mock ConductorRepositoryPort conductorRepository;
    @Mock SagaRepositoryPort sagaRepository;
    @Mock EventPublisherPort eventPublisher;

    @InjectMocks VehiculoRechazadoService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicAsignacionFallida",
                "fleetops.asignaciones.fallida");
    }

    @Test
    @DisplayName("procesar: dado vehiculo rechazado, libera conductor, marca SAGA FALLIDO y publica evento")
    void procesar_dadoVehiculoRechazado_compensaYPublica() {
        // Arrange
        UUID idAsignacion = UUID.randomUUID();
        String motivo     = "Sin vehículos disponibles del tipo CAMION";

        Conductor conductor = Conductor.builder()
                .id(UUID.randomUUID())
                .estado(EstadoConductor.RESERVADO)
                .build();
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
        when(conductorRepository.guardar(any())).thenReturn(conductor);
        when(sagaRepository.guardar(any())).thenReturn(saga);

        // Act
        service.procesar(idAsignacion, motivo);

        // Assert — conductor liberado
        assertThat(conductor.getEstado()).isEqualTo(EstadoConductor.DISPONIBLE);

        // Assert — SAGA marcada fallida con motivo
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.FALLIDO);
        assertThat(saga.getMotivoFallo()).isEqualTo(motivo);

        // Assert — evento de fallo publicado
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq("fleetops.asignaciones.fallida"), captor.capture());
        AsignacionFallidaEvent evento = (AsignacionFallidaEvent) captor.getValue();
        assertThat(evento.motivo()).isEqualTo(motivo);
    }

    @Test
    @DisplayName("procesar: dado asignacion inexistente, lanza excepcion sin modificar nada")
    void procesar_dadoAsignacionInexistente_lanzaExcepcion() {
        // Arrange
        UUID idAsignacion = UUID.randomUUID();
        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.procesar(idAsignacion, "motivo"))
                .isInstanceOf(NoSuchElementException.class);

        verify(conductorRepository, never()).guardar(any());
        verify(eventPublisher, never()).publicar(any(), any());
    }
}
