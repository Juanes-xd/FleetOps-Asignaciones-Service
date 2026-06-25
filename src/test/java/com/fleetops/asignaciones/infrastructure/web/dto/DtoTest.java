package com.fleetops.asignaciones.infrastructure.web.dto;

import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoTest {

    @Test
    void asignacionResponse_debeCrearRecordCorrectamente() {

        UUID idSaga = UUID.randomUUID();
        UUID idAsignacion = UUID.randomUUID();

        AsignacionResponse response = new AsignacionResponse(
                idSaga,
                idAsignacion,
                "mensaje"
        );

        assertThat(response.idSaga()).isEqualTo(idSaga);
        assertThat(response.idAsignacion()).isEqualTo(idAsignacion);
        assertThat(response.mensaje()).isEqualTo("mensaje");
    }

    @Test
    void crearAsignacionRequest_debeCrearRecordCorrectamente() {

        LocalDate inicio = LocalDate.now().plusDays(1);
        LocalDate fin = LocalDate.now().plusDays(5);

        CrearAsignacionRequest request = new CrearAsignacionRequest(
                "CAMION",
                inicio,
                fin
        );

        assertThat(request.tipoVehiculo()).isEqualTo("CAMION");
        assertThat(request.fechaInicio()).isEqualTo(inicio);
        assertThat(request.fechaFin()).isEqualTo(fin);
    }

    @Test
    void sagaEstadoResponse_debeCrearRecordCorrectamente() {

        UUID idSaga = UUID.randomUUID();
        UUID vehiculoId = UUID.randomUUID();

        SagaEstadoResponse response = new SagaEstadoResponse(
                idSaga,
                EstadoSaga.COMPLETADO,
                vehiculoId,
                null
        );

        assertThat(response.idSaga()).isEqualTo(idSaga);
        assertThat(response.estado()).isEqualTo(EstadoSaga.COMPLETADO);
        assertThat(response.vehiculoId()).isEqualTo(vehiculoId);
    }
}
