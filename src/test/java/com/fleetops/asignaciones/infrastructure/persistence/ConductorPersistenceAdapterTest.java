package com.fleetops.asignaciones.infrastructure.persistence;

import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.model.Conductor;
import com.fleetops.asignaciones.infrastructure.persistence.jpa.ConductorJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConductorPersistenceAdapterTest {

    @Mock
    private ConductorJpaRepository jpaRepository;

    @InjectMocks
    private ConductorPersistenceAdapter adapter;

    @Test
    void buscarDisponiblePorTipoVehiculo() {

        Conductor conductor = mock(Conductor.class);

        when(jpaRepository.findFirstByTipoVehiculoAndEstado(
                "CAMION",
                EstadoConductor.DISPONIBLE))
                .thenReturn(Optional.of(conductor));

        Optional<Conductor> resultado =
                adapter.buscarDisponiblePorTipoVehiculo("CAMION");

        assertThat(resultado).contains(conductor);
    }

    @Test
    void guardar() {

        Conductor conductor = mock(Conductor.class);

        when(jpaRepository.save(conductor))
                .thenReturn(conductor);

        Conductor resultado = adapter.guardar(conductor);

        assertThat(resultado).isEqualTo(conductor);
    }

    @Test
    void buscarPorId() {

        UUID id = UUID.randomUUID();
        Conductor conductor = mock(Conductor.class);

        when(jpaRepository.findById(id))
                .thenReturn(Optional.of(conductor));

        Optional<Conductor> resultado = adapter.buscarPorId(id);

        assertThat(resultado).contains(conductor);
    }
}