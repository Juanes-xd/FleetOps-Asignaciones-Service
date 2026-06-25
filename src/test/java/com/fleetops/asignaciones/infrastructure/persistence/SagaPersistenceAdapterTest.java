package com.fleetops.asignaciones.infrastructure.persistence;

import com.fleetops.asignaciones.domain.model.SagaRegistro;
import com.fleetops.asignaciones.infrastructure.persistence.jpa.SagaJpaRepository;
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
class SagaPersistenceAdapterTest {

    @Mock
    private SagaJpaRepository jpaRepository;

    @InjectMocks
    private SagaPersistenceAdapter adapter;

    @Test
    void guardar() {

        SagaRegistro saga = mock(SagaRegistro.class);

        when(jpaRepository.save(saga))
                .thenReturn(saga);

        SagaRegistro resultado = adapter.guardar(saga);

        assertThat(resultado).isEqualTo(saga);
    }

    @Test
    void buscarPorId() {

        UUID id = UUID.randomUUID();
        SagaRegistro saga = mock(SagaRegistro.class);

        when(jpaRepository.findById(id))
                .thenReturn(Optional.of(saga));

        Optional<SagaRegistro> resultado = adapter.buscarPorId(id);

        assertThat(resultado).contains(saga);
    }

    @Test
    void buscarPorAsignacionId() {

        UUID idAsignacion = UUID.randomUUID();
        SagaRegistro saga = mock(SagaRegistro.class);

        when(jpaRepository.findByAsignacionId(idAsignacion))
                .thenReturn(Optional.of(saga));

        Optional<SagaRegistro> resultado =
                adapter.buscarPorAsignacionId(idAsignacion);

        assertThat(resultado).contains(saga);
    }
}