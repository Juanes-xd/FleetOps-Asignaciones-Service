package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.ConsultarSagaUseCase;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultarSagaService")
class ConsultarSagaServiceTest {

    @Mock SagaRepositoryPort sagaRepository;
    @InjectMocks ConsultarSagaService service;

    @Test
    @DisplayName("consultar: dado idSaga existente con estado COMPLETADO, retorna estado correcto")
    void consultar_dadoSagaCompletada_retornaEstado() {
        // Arrange
        UUID idSaga     = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();

        SagaRegistro saga = SagaRegistro.builder()
                .id(idSaga)
                .asignacion(Asignacion.builder().id(UUID.randomUUID()).build())
                .estado(EstadoSaga.COMPLETADO)
                .vehiculoId(idVehiculo)
                .build();

        when(sagaRepository.buscarPorId(idSaga)).thenReturn(Optional.of(saga));

        // Act
        ConsultarSagaUseCase.Result result = service.consultar(idSaga);

        // Assert
        assertThat(result.idSaga()).isEqualTo(idSaga);
        assertThat(result.estado()).isEqualTo(EstadoSaga.COMPLETADO);
        assertThat(result.vehiculoId()).isEqualTo(idVehiculo);
        assertThat(result.motivoFallo()).isNull();
    }

    @Test
    @DisplayName("consultar: dado saga FALLIDO, retorna motivo de fallo")
    void consultar_dadoSagaFallida_retornaMotivoFallo() {
        // Arrange
        UUID idSaga = UUID.randomUUID();
        SagaRegistro saga = SagaRegistro.builder()
                .id(idSaga)
                .asignacion(Asignacion.builder().id(UUID.randomUUID()).build())
                .estado(EstadoSaga.FALLIDO)
                .motivoFallo("Sin vehículos disponibles")
                .build();

        when(sagaRepository.buscarPorId(idSaga)).thenReturn(Optional.of(saga));

        // Act
        ConsultarSagaUseCase.Result result = service.consultar(idSaga);

        // Assert
        assertThat(result.estado()).isEqualTo(EstadoSaga.FALLIDO);
        assertThat(result.motivoFallo()).isEqualTo("Sin vehículos disponibles");
        assertThat(result.vehiculoId()).isNull();
    }

    @Test
    @DisplayName("consultar: dado saga PENDIENTE_VEHICULO, refleja estado intermedio correctamente")
    void consultar_dadoSagaPendiente_retornaEstadoIntermedio() {
        // Arrange
        UUID idSaga = UUID.randomUUID();
        SagaRegistro saga = SagaRegistro.builder()
                .id(idSaga)
                .asignacion(Asignacion.builder().id(UUID.randomUUID()).build())
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        when(sagaRepository.buscarPorId(idSaga)).thenReturn(Optional.of(saga));

        // Act
        ConsultarSagaUseCase.Result result = service.consultar(idSaga);

        // Assert
        assertThat(result.estado()).isEqualTo(EstadoSaga.PENDIENTE_VEHICULO);
        assertThat(result.vehiculoId()).isNull();
        assertThat(result.motivoFallo()).isNull();
    }

    @Test
    @DisplayName("consultar: dado idSaga inexistente, lanza NoSuchElementException")
    void consultar_dadoIdSagaInexistente_lanzaExcepcion() {
        // Arrange
        UUID idSaga = UUID.randomUUID();
        when(sagaRepository.buscarPorId(idSaga)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.consultar(idSaga))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(idSaga.toString());
    }
}
