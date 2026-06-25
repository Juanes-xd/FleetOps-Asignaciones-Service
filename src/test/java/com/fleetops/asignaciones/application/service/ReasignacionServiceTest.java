package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.FallaMecanicaRecibidaEvent;
import com.fleetops.asignaciones.domain.event.VehiculoLiberadoEvent;
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
@DisplayName("ReasignacionService — reacción coreografiada a falla mecánica de Incidentes")
class ReasignacionServiceTest {

    @Mock AsignacionRepositoryPort asignacionRepository;
    @Mock ConductorRepositoryPort conductorRepository;
    @Mock SagaRepositoryPort sagaRepository;
    @Mock EventPublisherPort eventPublisher;

    @InjectMocks ReasignacionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicVehiculosLiberar",
                "fleetops.vehiculos.liberar");
    }

    @Test
    @DisplayName("procesar: dado evento de falla mecanica, libera conductor, actualiza SAGA y publica VehiculoLiberadoEvent")
    void procesar_dadoFallaMecanica_liberaConductorYPublicaEvento() {
        // Arrange
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo   = UUID.randomUUID();
        UUID idIncidente  = UUID.randomUUID();

        Conductor conductor = Conductor.builder()
                .id(UUID.randomUUID())
                .estado(EstadoConductor.RESERVADO)
                .build();
        Asignacion asignacion = Asignacion.builder()
                .id(idAsignacion)
                .conductor(conductor)
                .vehiculoId(idVehiculo)
                .build();
        SagaRegistro saga = SagaRegistro.builder()
                .id(UUID.randomUUID())
                .asignacion(asignacion)
                .estado(EstadoSaga.COMPLETADO)
                .build();

        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.of(asignacion));
        when(sagaRepository.buscarPorAsignacionId(idAsignacion)).thenReturn(Optional.of(saga));
        when(conductorRepository.guardar(any())).thenReturn(conductor);
        when(sagaRepository.guardar(any())).thenReturn(saga);

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                idIncidente, idVehiculo, idAsignacion, "Motor averiado");

        // Act
        service.procesar(evento);

        // Assert — conductor liberado
        assertThat(conductor.getEstado()).isEqualTo(EstadoConductor.DISPONIBLE);

        // Assert — SAGA en estado de compensación
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.PENDIENTE_LIBERACION);

        // Assert — VehiculoLiberadoEvent publicado para que Vehículos reaccione
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq("fleetops.vehiculos.liberar"), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(VehiculoLiberadoEvent.class);

        VehiculoLiberadoEvent eventoPublicado = (VehiculoLiberadoEvent) captor.getValue();
        assertThat(eventoPublicado.idVehiculo()).isEqualTo(idVehiculo);
    }

    @Test
    @DisplayName("procesar: dado asignacion inexistente, lanza excepcion sin publicar evento")
    void procesar_dadoAsignacionInexistente_lanzaExcepcion() {
        // Arrange
        UUID idAsignacion = UUID.randomUUID();
        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.empty());

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                UUID.randomUUID(), UUID.randomUUID(), idAsignacion, "falla");

        // Act & Assert
        assertThatThrownBy(() -> service.procesar(evento))
                .isInstanceOf(NoSuchElementException.class);

        verify(eventPublisher, never()).publicar(any(), any());
    }

    @Test
    @DisplayName("procesar: dado saga inexistente, lanza excepcion sin publicar evento")
    void procesar_dadoSagaInexistente_lanzaExcepcion() {

        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();

        Conductor conductor = Conductor.builder()
                .id(UUID.randomUUID())
                .estado(EstadoConductor.RESERVADO)
                .build();

        Asignacion asignacion = Asignacion.builder()
                .id(idAsignacion)
                .conductor(conductor)
                .vehiculoId(idVehiculo)
                .build();

        when(asignacionRepository.buscarPorId(idAsignacion))
                .thenReturn(Optional.of(asignacion));

        when(sagaRepository.buscarPorAsignacionId(idAsignacion))
                .thenReturn(Optional.empty());

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                UUID.randomUUID(),
                idVehiculo,
                idAsignacion,
                "Motor averiado"
        );

        assertThatThrownBy(() -> service.procesar(evento))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Saga no encontrada");

        verify(eventPublisher, never()).publicar(any(), any());
    }
}
