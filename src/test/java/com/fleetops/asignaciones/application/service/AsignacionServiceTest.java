package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.CrearAsignacionUseCase;
import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.VehiculoSolicitadoEvent;
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

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsignacionService — coreografía sin OutboxWorker")
class AsignacionServiceTest {

    @Mock ConductorRepositoryPort conductorRepository;
    @Mock AsignacionRepositoryPort asignacionRepository;
    @Mock SagaRepositoryPort sagaRepository;
    @Mock EventPublisherPort eventPublisher;

    @InjectMocks AsignacionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicVehiculosSolicitar",
                "fleetops.vehiculos.solicitar");
    }

    @Test
    @DisplayName("ejecutar: dado conductor disponible, reserva conductor, crea SAGA y publica evento Kafka")
    void ejecutar_dadoConductorDisponible_creaYPublicaEvento() {
        // Arrange
        Conductor conductor = Conductor.builder()
                .id(UUID.randomUUID())
                .tipoVehiculo("CAMION")
                .estado(EstadoConductor.DISPONIBLE)
                .build();

        Asignacion asignacionGuardada = Asignacion.builder()
                .id(UUID.randomUUID())
                .conductor(conductor)
                .tipoVehiculo("CAMION")
                .fechaInicio(LocalDate.now().plusDays(1))
                .fechaFin(LocalDate.now().plusDays(5))
                .build();

        SagaRegistro sagaGuardada = SagaRegistro.builder()
                .id(UUID.randomUUID())
                .asignacion(asignacionGuardada)
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        when(conductorRepository.buscarDisponiblePorTipoVehiculo("CAMION"))
                .thenReturn(Optional.of(conductor));
        when(conductorRepository.guardar(any())).thenReturn(conductor);
        when(asignacionRepository.guardar(any())).thenReturn(asignacionGuardada);
        when(sagaRepository.guardar(any())).thenReturn(sagaGuardada);

        CrearAsignacionUseCase.Command command = new CrearAsignacionUseCase.Command(
                "CAMION",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(5)
        );

        // Act
        CrearAsignacionUseCase.Result result = service.ejecutar(command);

        // Assert — resultado correcto
        assertThat(result.idSaga()).isEqualTo(sagaGuardada.getId());
        assertThat(result.idAsignacion()).isEqualTo(asignacionGuardada.getId());

        // Assert — conductor reservado
        assertThat(conductor.getEstado()).isEqualTo(EstadoConductor.RESERVADO);

        // Assert — evento publicado directamente (sin OutboxWorker)
        ArgumentCaptor<Object> eventoCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(
                eq("fleetops.vehiculos.solicitar"), eventoCaptor.capture());
        assertThat(eventoCaptor.getValue()).isInstanceOf(VehiculoSolicitadoEvent.class);

        VehiculoSolicitadoEvent evento = (VehiculoSolicitadoEvent) eventoCaptor.getValue();
        assertThat(evento.idAsignacion()).isEqualTo(asignacionGuardada.getId());
        assertThat(evento.tipoVehiculo()).isEqualTo("CAMION");
    }

    @Test
    @DisplayName("ejecutar: dado ningún conductor disponible, lanza excepción y NO publica evento")
    void ejecutar_sinConductorDisponible_nuncaPublicaEvento() {
        // Arrange
        when(conductorRepository.buscarDisponiblePorTipoVehiculo("MOTO"))
                .thenReturn(Optional.empty());

        CrearAsignacionUseCase.Command command = new CrearAsignacionUseCase.Command(
                "MOTO",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3)
        );

        // Act & Assert
        assertThatThrownBy(() -> service.ejecutar(command))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("MOTO");

        verify(eventPublisher, never()).publicar(any(), any());
        verify(sagaRepository, never()).guardar(any());
    }
}
