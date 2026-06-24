package com.fleetops.asignaciones.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Pruebas unitarias de Licencia")
class LicenciaTest {

    @Test
    @DisplayName("estaVigente retorna true cuando la licencia no ha vencido")
    void estaVigente_retornaTrue() {

        Licencia licencia = Licencia.builder()
                .fechaVencimiento(LocalDate.now().plusDays(30))
                .build();

        assertThat(licencia.estaVigente()).isTrue();
    }

    @Test
    @DisplayName("estaVigente retorna false cuando la licencia ya venció")
    void estaVigente_retornaFalse() {

        Licencia licencia = Licencia.builder()
                .fechaVencimiento(LocalDate.now().minusDays(1))
                .build();

        assertThat(licencia.estaVigente()).isFalse();
    }

}
