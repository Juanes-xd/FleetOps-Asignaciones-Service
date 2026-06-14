package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.ProcesarFallaMecanicaUseCase;
import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.FallaMecanicaRecibidaEvent;
import com.fleetops.asignaciones.domain.event.VehiculoLiberadoEvent;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.domain.model.Conductor;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Reacción coreografiada a una falla mecánica reportada por Incidentes.
 *
 * Cuando Incidentes publica una falla mecánica:
 *  1. Se libera el conductor de la asignación afectada
 *  2. La SAGA pasa a PENDIENTE_LIBERACION
 *  3. Se publica VehiculoLiberadoEvent para que Vehículos libere el vehículo
 *
 * Vehículos reacciona a VehiculoLiberadoEvent de forma autónoma —
 * ningún orquestador coordina este paso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasignacionService implements ProcesarFallaMecanicaUseCase {

    private final AsignacionRepositoryPort asignacionRepository;
    private final ConductorRepositoryPort conductorRepository;
    private final SagaRepositoryPort sagaRepository;
    private final EventPublisherPort eventPublisher;

    @Value("${asignaciones.kafka.topics.vehiculos-liberar}")
    private String topicVehiculosLiberar;

    @Override
    @Transactional
    public void procesar(FallaMecanicaRecibidaEvent evento) {
        log.info("Coreografia: falla mecanica recibida. Incidente: {} Vehiculo: {}",
                evento.idIncidente(), evento.idVehiculo());

        Asignacion asignacion = asignacionRepository
                .buscarPorId(evento.idAsignacion())
                .orElseThrow(() -> new NoSuchElementException(
                        "Asignacion no encontrada: " + evento.idAsignacion()));

        // Liberar conductor
        Conductor conductor = asignacion.getConductor();
        conductor.liberar();
        conductorRepository.guardar(conductor);

        // Actualizar SAGA a estado de compensación
        SagaRegistro saga = sagaRepository.buscarPorAsignacionId(evento.idAsignacion())
                .orElseThrow(() -> new NoSuchElementException(
                        "Saga no encontrada para asignacion: " + evento.idAsignacion()));

        saga.marcarPendienteLiberacion();
        sagaRepository.guardar(saga);

        // Publicar evento — Vehículos reacciona y libera el vehículo (coreografía)
        eventPublisher.publicar(topicVehiculosLiberar, new VehiculoLiberadoEvent(
                saga.getId(),
                evento.idVehiculo()
        ));

        log.info("Coreografia: VehiculoLiberadoEvent publicado para saga {}", saga.getId());
    }
}
