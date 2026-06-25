package com.fleetops.asignaciones.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Pruebas unitarias de Asignacion")
class AsignacionTest {

    @Test
    @DisplayName("asignarVehiculo asigna correctamente el vehiculoId")
    void asignarVehiculo_correctamente() {

        Asignacion asignacion = Asignacion.builder()
                .build();

        UUID vehiculoId = UUID.randomUUID();

        asignacion.asignarVehiculo(vehiculoId);

        assertThat(asignacion.getVehiculoId())
                .isEqualTo(vehiculoId);
    }

    @Test
    @DisplayName("asignarVehiculo lanza excepción cuando vehiculoId es nulo")
    void asignarVehiculo_nulo() {

        Asignacion asignacion = Asignacion.builder()
                .build();

        assertThatThrownBy(() -> asignacion.asignarVehiculo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehiculoId");
    }
    @Test
    @DisplayName("onCreate asigna la fecha de creación")
    void onCreate_asignaFechaCreacion() {

        Asignacion asignacion = Asignacion.builder().build();

        org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(asignacion, "onCreate");

        assertThat(asignacion.getCreadaEn())
                .isNotNull();

    }

}
