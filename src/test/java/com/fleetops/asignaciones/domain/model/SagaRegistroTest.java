package com.fleetops.asignaciones.domain.model;

import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Pruebas unitarias de SagaRegistro")
class SagaRegistroTest {

    @Test
    @DisplayName("marcarCompletada cambia el estado a COMPLETADO y guarda el vehiculoId")
    void marcarCompletada() {

        SagaRegistro saga = SagaRegistro.builder()
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        UUID vehiculoId = UUID.randomUUID();

        saga.marcarCompletada(vehiculoId);

        assertThat(saga.getEstado())
                .isEqualTo(EstadoSaga.COMPLETADO);

        assertThat(saga.getVehiculoId())
                .isEqualTo(vehiculoId);
    }

    @Test
    @DisplayName("marcarFallida cambia el estado a FALLIDO y guarda el motivo")
    void marcarFallida() {

        SagaRegistro saga = SagaRegistro.builder()
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        String motivo = "Vehículo no disponible";

        saga.marcarFallida(motivo);

        assertThat(saga.getEstado())
                .isEqualTo(EstadoSaga.FALLIDO);

        assertThat(saga.getMotivoFallo())
                .isEqualTo(motivo);
    }

    @Test
    @DisplayName("marcarPendienteLiberacion cambia el estado a PENDIENTE_LIBERACION")
    void marcarPendienteLiberacion() {

        SagaRegistro saga = SagaRegistro.builder()
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        saga.marcarPendienteLiberacion();

        assertThat(saga.getEstado())
                .isEqualTo(EstadoSaga.PENDIENTE_LIBERACION);
    }
    @Test
    @DisplayName("onCreate inicializa las fechas de creación y actualización")
    void onCreate_inicializaFechas() {

        SagaRegistro saga = SagaRegistro.builder().build();

        org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(saga, "onCreate");

        assertThat(saga.getCreadaEn()).isNotNull();
        assertThat(saga.getActualizadaEn()).isNotNull();

    }

    @Test
    @DisplayName("onUpdate actualiza la fecha de actualización")
    void onUpdate_actualizaFecha() {

        SagaRegistro saga = SagaRegistro.builder().build();

        org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(saga, "onUpdate");

        assertThat(saga.getActualizadaEn()).isNotNull();

    }

}
