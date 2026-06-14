package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.ConsultarSagaUseCase;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Implementa únicamente ConsultarSagaUseCase.
 * Separado de AsignacionService para evitar ambigüedad en tipos Result
 * entre use cases y respetar SRP.
 */
@Service
@RequiredArgsConstructor
public class ConsultarSagaService implements ConsultarSagaUseCase {

    private final SagaRepositoryPort sagaRepository;

    @Override
    @Transactional(readOnly = true)
    public ConsultarSagaUseCase.Result consultar(UUID idSaga) {
        SagaRegistro saga = sagaRepository.buscarPorId(idSaga)
                .orElseThrow(() -> new NoSuchElementException(
                        "Saga no encontrada: " + idSaga));

        return new ConsultarSagaUseCase.Result(
                saga.getId(),
                saga.getEstado(),
                saga.getMotivoFallo(),
                saga.getVehiculoId()
        );
    }
}
