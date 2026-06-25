package com.fleetops.asignaciones.infrastructure.persistence;

import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.infrastructure.persistence.jpa.AsignacionJpaRepository;
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
class AsignacionPersistenceAdapterTest {

    @Mock
    private AsignacionJpaRepository jpaRepository;

    @InjectMocks
    private AsignacionPersistenceAdapter adapter;

    @Test
    void guardar_debeDelegarEnJpaRepository() {

        Asignacion asignacion = mock(Asignacion.class);

        when(jpaRepository.save(asignacion))
                .thenReturn(asignacion);

        Asignacion resultado = adapter.guardar(asignacion);

        assertThat(resultado).isEqualTo(asignacion);
        verify(jpaRepository).save(asignacion);
    }

    @Test
    void buscarPorId_debeDelegarEnJpaRepository() {

        UUID id = UUID.randomUUID();
        Asignacion asignacion = mock(Asignacion.class);

        when(jpaRepository.findById(id))
                .thenReturn(Optional.of(asignacion));

        Optional<Asignacion> resultado = adapter.buscarPorId(id);

        assertThat(resultado).contains(asignacion);
        verify(jpaRepository).findById(id);
    }
}