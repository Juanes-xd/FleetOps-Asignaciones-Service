package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoRechazadoUseCase;
import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.event.AsignacionFallidaEvent;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.domain.model.Conductor;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Reacción coreografiada al evento VehiculoNoDisponibleEvent.
 *
 * Cuando Vehículos no puede asignar un vehículo, publica un evento
 * de rechazo. Este servicio reacciona ejecutando la compensación:
 *  - Libera el conductor que había sido reservado
 *  - Marca la SAGA como FALLIDO
 *  - Publica AsignacionFallidaEvent para trazabilidad y auditoría
 *
 * No hay orquestador que coordine esto — es la reacción natural
 * del microservicio de Asignaciones al evento de rechazo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehiculoRechazadoService implements ProcesarVehiculoRechazadoUseCase {

    private final AsignacionRepositoryPort asignacionRepository;
    private final ConductorRepositoryPort conductorRepository;
    private final SagaRepositoryPort sagaRepository;
    private final EventPublisherPort eventPublisher;

    @Value("${asignaciones.kafka.topics.asignacion-fallida}")
    private String topicAsignacionFallida;

    @Override
    @Transactional
    public void procesar(UUID idAsignacion, String motivo) {
        Asignacion asignacion = asignacionRepository.buscarPorId(idAsignacion)
                .orElseThrow(() -> new NoSuchElementException(
                        "Asignacion no encontrada: " + idAsignacion));

        // Compensación: liberar el conductor reservado
        Conductor conductor = asignacion.getConductor();
        conductor.liberar();
        conductorRepository.guardar(conductor);

        SagaRegistro saga = sagaRepository.buscarPorAsignacionId(idAsignacion)
                .orElseThrow(() -> new NoSuchElementException(
                        "Saga no encontrada para asignacion: " + idAsignacion));

        saga.marcarFallida(motivo);
        sagaRepository.guardar(saga);

        eventPublisher.publicar(topicAsignacionFallida, new AsignacionFallidaEvent(
                saga.getId(),
                asignacion.getId(),
                motivo
        ));

        log.warn("Coreografia: asignacion {} fallida. Conductor {} liberado. Motivo: {}",
                idAsignacion, conductor.getId(), motivo);
    }
}
