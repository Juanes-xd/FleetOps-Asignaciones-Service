package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.application.port.out.VehiculoConsultaPort;
import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.FallaMecanicaRecibidaEvent;
import com.fleetops.asignaciones.domain.event.VehiculoLiberadoEvent;
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

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.Date;

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
    @Mock VehiculoConsultaPort vehiculoConsultaPort;

    @InjectMocks ReasignacionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicVehiculosLiberar",
                "fleetops.vehiculos.liberar");
        ReflectionTestUtils.setField(service, "topicVehiculosSolicitar",
                "fleetops.vehiculos.solicitar");
    }

    @Test
    @DisplayName("procesar: incidente mecanico grave resuelve la placa, libera vehiculo, marca SAGA en espera y publica liberacion + solicitud")
    void procesar_dadoIncidenteMecanicoGrave_liberaVehiculoYPublicaEventos() {
        // Arrange
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo   = UUID.randomUUID();
        String placa      = "AUT004";
        String idIncidente = UUID.randomUUID().toString();

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

        when(vehiculoConsultaPort.buscarIdPorPlaca(placa)).thenReturn(Optional.of(idVehiculo));
        when(asignacionRepository.buscarPorVehiculoId(idVehiculo)).thenReturn(Optional.of(asignacion));
        when(sagaRepository.buscarPorAsignacionId(idAsignacion)).thenReturn(Optional.of(saga));
        when(asignacionRepository.guardar(any())).thenReturn(asignacion);
        when(sagaRepository.guardar(any())).thenReturn(saga);

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                idIncidente,
                placa,
                "Motor averiado",
                UUID.randomUUID(),
                "MECANICO",
                "GRAVE",
                new Date());

        // Act
        service.procesar(evento);

        // Assert — vehiculo liberado en la asignacion
        assertThat(asignacion.getVehiculoId()).isNull();

        // Assert — SAGA en estado de compensación
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.PENDIENTE_LIBERACION);

        // Assert — VehiculoLiberadoEvent publicado para que Vehículos reaccione, con el idVehiculo resuelto (no la placa)
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq("fleetops.vehiculos.liberar"), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(VehiculoLiberadoEvent.class);

        VehiculoLiberadoEvent eventoPublicado = (VehiculoLiberadoEvent) captor.getValue();
        assertThat(eventoPublicado.idVehiculo()).isEqualTo(idVehiculo);

        verify(eventPublisher).publicar(eq("fleetops.vehiculos.solicitar"), any(VehiculoSolicitadoEvent.class));
    }

    @Test
    @DisplayName("procesar: incidente humano grave reasigna conductor disponible")
    void procesar_dadoIncidenteHumanoGrave_reasignaConductor() {

        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();
        UUID conductorAntiguoId = UUID.randomUUID();
        UUID conductorNuevoId = UUID.randomUUID();

        Conductor conductor = Conductor.builder()
                .id(conductorAntiguoId)
                .estado(EstadoConductor.RESERVADO)
                .tipoVehiculo("CAMION")
                .build();
        Conductor conductorNuevo = Conductor.builder()
                .id(conductorNuevoId)
                .estado(EstadoConductor.DISPONIBLE)
                .tipoVehiculo("CAMION")
                .build();

        Asignacion asignacion = Asignacion.builder()
                .id(idAsignacion)
                .conductor(conductor)
                .vehiculoId(idVehiculo)
                .tipoVehiculo("CAMION")
                .build();

        when(asignacionRepository.buscarPorConductorId(conductorAntiguoId))
                .thenReturn(Optional.of(asignacion));
        when(conductorRepository.buscarDisponiblePorTipoVehiculo("CAMION")).thenReturn(Optional.of(conductorNuevo));
        when(conductorRepository.guardar(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(asignacionRepository.guardar(any())).thenReturn(asignacion);

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                UUID.randomUUID().toString(),
                "AUT004",
                "Conductor incapacitado",
                conductorAntiguoId,
                "HUMANO",
                "GRAVE",
                new Date()
        );

        service.procesar(evento);

        assertThat(conductor.getEstado()).isEqualTo(EstadoConductor.DISPONIBLE);
        assertThat(conductorNuevo.getEstado()).isEqualTo(EstadoConductor.RESERVADO);
        assertThat(asignacion.getConductor()).isEqualTo(conductorNuevo);

        verify(eventPublisher, never()).publicar(eq("fleetops.vehiculos.liberar"), any());
        verify(eventPublisher, never()).publicar(eq("fleetops.vehiculos.solicitar"), any());
        verifyNoInteractions(vehiculoConsultaPort);
    }

    @Test
    @DisplayName("procesar: dado incidente no grave, no aplica compensacion")
    void procesar_dadoIncidenteNoGrave_noHaceNada() {
        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                UUID.randomUUID().toString(),
                "AUT004",
                "Golpe menor",
                UUID.randomUUID(),
                "MECANICO",
                "LEVE",
                new Date());

        service.procesar(evento);

        verifyNoInteractions(asignacionRepository, conductorRepository, sagaRepository, eventPublisher, vehiculoConsultaPort);
    }

    @Test
    @DisplayName("procesar: dado incidente mecanico con placa sin vehiculo registrado, lanza excepcion")
    void procesar_dadoPlacaSinVehiculoRegistrado_lanzaExcepcion() {
        String placa = "ZZZ999";
        when(vehiculoConsultaPort.buscarIdPorPlaca(placa)).thenReturn(Optional.empty());

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                UUID.randomUUID().toString(), placa, "falla", UUID.randomUUID(), "MECANICO", "GRAVE", new Date());

        assertThatThrownBy(() -> service.procesar(evento))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Vehiculo no encontrado para placa");

        verifyNoInteractions(asignacionRepository, eventPublisher);
    }

    @Test
    @DisplayName("procesar: dado incidente mecanico con vehiculo inexistente, lanza excepcion")
    void procesar_dadoVehiculoInexistente_lanzaExcepcion() {
        UUID idVehiculo = UUID.randomUUID();
        String placa = "AUT004";
        when(vehiculoConsultaPort.buscarIdPorPlaca(placa)).thenReturn(Optional.of(idVehiculo));
        when(asignacionRepository.buscarPorVehiculoId(idVehiculo)).thenReturn(Optional.empty());

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                UUID.randomUUID().toString(), placa, "falla", UUID.randomUUID(), "MECANICO", "GRAVE", new Date());

        assertThatThrownBy(() -> service.procesar(evento))
                .isInstanceOf(NoSuchElementException.class);

        verify(eventPublisher, never()).publicar(any(), any());
    }

    @Test
    @DisplayName("procesar: dado conductor inexistente, lanza excepcion")
    void procesar_dadoConductorInexistente_lanzaExcepcion() {
        UUID idConductor = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();

        Conductor conductor = Conductor.builder()
                .id(UUID.randomUUID())
                .estado(EstadoConductor.RESERVADO)
                .build();

        Asignacion asignacion = Asignacion.builder()
                .id(UUID.randomUUID())
                .conductor(conductor)
                .vehiculoId(idVehiculo)
                .build();

        when(asignacionRepository.buscarPorConductorId(idConductor)).thenReturn(Optional.of(asignacion));
        when(conductorRepository.buscarDisponiblePorTipoVehiculo(any())).thenReturn(Optional.empty());

        FallaMecanicaRecibidaEvent evento = new FallaMecanicaRecibidaEvent(
                UUID.randomUUID().toString(),
                "AUT004",
                "Conductor incapacitado",
                idConductor,
                "HUMANO",
                "GRAVE",
                new Date()
        );

        assertThatThrownBy(() -> service.procesar(evento))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No hay conductor disponible");

        verify(eventPublisher, never()).publicar(any(), any());
    }
}
