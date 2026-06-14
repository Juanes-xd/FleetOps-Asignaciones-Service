package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoAsignadoUseCase;
import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.event.AsignacionCompletadaEvent;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Reacción coreografiada al evento VehiculoAsignadoEvent.
 *
 * En coreografía, este servicio NO es llamado por un orquestador.
 * Es invocado por KafkaVehiculosConsumer cuando Vehículos
 * publica que asignó el vehículo exitosamente.
 *
 * Responsabilidades:
 *  - Actualizar la asignación con el vehiculoId confirmado
 *  - Marcar la SAGA como COMPLETADO
 *  - Publicar AsignacionCompletadaEvent para que otros servicios reaccionen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehiculoAsignadoService implements ProcesarVehiculoAsignadoUseCase {

    private final AsignacionRepositoryPort asignacionRepository;
    private final SagaRepositoryPort sagaRepository;
    private final EventPublisherPort eventPublisher;

    @Value("${asignaciones.kafka.topics.asignacion-completada}")
    private String topicAsignacionCompletada;

    @Override
    @Transactional
    public void procesar(UUID idAsignacion, UUID idVehiculo) {
        Asignacion asignacion = asignacionRepository.buscarPorId(idAsignacion)
                .orElseThrow(() -> new NoSuchElementException(
                        "Asignacion no encontrada: " + idAsignacion));

        asignacion.asignarVehiculo(idVehiculo);
        asignacionRepository.guardar(asignacion);

        SagaRegistro saga = sagaRepository.buscarPorAsignacionId(idAsignacion)
                .orElseThrow(() -> new NoSuchElementException(
                        "Saga no encontrada para asignacion: " + idAsignacion));

        saga.marcarCompletada(idVehiculo);
        sagaRepository.guardar(saga);

        eventPublisher.publicar(topicAsignacionCompletada, new AsignacionCompletadaEvent(
                saga.getId(),
                asignacion.getId(),
                idVehiculo,
                asignacion.getConductor().getId()
        ));

        log.info("Coreografia: asignacion {} completada con vehiculo {}", idAsignacion, idVehiculo);
    }
}
