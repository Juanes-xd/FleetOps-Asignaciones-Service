package com.fleetops.asignaciones.domain.model;

import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Pruebas unitarias de Conductor")
class ConductorTest {

    @Test
    @DisplayName("estaDisponible retorna true cuando el estado es DISPONIBLE")
    void estaDisponible_retornaTrue() {

        Conductor conductor = Conductor.builder()
                .estado(EstadoConductor.DISPONIBLE)
                .build();

        assertThat(conductor.estaDisponible()).isTrue();
    }

    @Test
    @DisplayName("estaDisponible retorna false cuando el estado no es DISPONIBLE")
    void estaDisponible_retornaFalse() {

        Conductor conductor = Conductor.builder()
                .estado(EstadoConductor.RESERVADO)
                .build();

        assertThat(conductor.estaDisponible()).isFalse();
    }

    @Test
    @DisplayName("reservar cambia el estado a RESERVADO")
    void reservar_conductorDisponible() {

        Conductor conductor = Conductor.builder()
                .estado(EstadoConductor.DISPONIBLE)
                .build();

        conductor.reservar();

        assertThat(conductor.getEstado())
                .isEqualTo(EstadoConductor.RESERVADO);
    }

    @Test
    @DisplayName("reservar lanza excepción cuando el conductor no está disponible")
    void reservar_conductorNoDisponible() {

        Conductor conductor = Conductor.builder()
                .estado(EstadoConductor.RESERVADO)
                .build();

        assertThatThrownBy(conductor::reservar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    @DisplayName("liberar cambia el estado a DISPONIBLE")
    void liberar_conductor() {

        Conductor conductor = Conductor.builder()
                .estado(EstadoConductor.RESERVADO)
                .build();

        conductor.liberar();

        assertThat(conductor.getEstado())
                .isEqualTo(EstadoConductor.DISPONIBLE);
    }

}
