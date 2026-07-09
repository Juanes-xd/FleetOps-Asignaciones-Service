package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.ProcesarFallaMecanicaUseCase;
import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.application.port.out.VehiculoConsultaPort;
import com.fleetops.asignaciones.domain.event.VehiculoSolicitadoEvent;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Reacción coreografiada a incidentes reportados por Incidentes.
 *
 * Regla actual:
 *  - MECANICO + GRAVE -> liberar el vehículo afectado y pedir uno nuevo
 *  - HUMANO + GRAVE   -> liberar el conductor afectado y reasignar otro conductor
 *  - cualquier otra combinación -> solo se registra y no dispara compensación
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReasignacionService implements ProcesarFallaMecanicaUseCase {

    private final AsignacionRepositoryPort asignacionRepository;
    private final ConductorRepositoryPort conductorRepository;
    private final SagaRepositoryPort sagaRepository;
    private final EventPublisherPort eventPublisher;
    private final VehiculoConsultaPort vehiculoConsultaPort;

    @Value("${asignaciones.kafka.topics.vehiculos-liberar}")
    private String topicVehiculosLiberar;

    @Value("${asignaciones.kafka.topics.vehiculos-solicitar}")
    private String topicVehiculosSolicitar;

    @Override
    @Transactional
    public void procesar(FallaMecanicaRecibidaEvent evento) {
        log.info("Coreografia: incidente recibido. Incidente: {} Tipo: {} Severidad: {} Vehiculo: {} Conductor: {}",
                evento.incident_id(), evento.incident_type(), evento.severity(), evento.vehicle_id(), evento.driver_id());

        if (!esGrave(evento.severity())) {
            log.info("Incidente {} ignorado porque no es grave", evento.incident_id());
            return;
        }

        if (esMecanico(evento.incident_type())) {
            procesarIncidenteMecanico(evento);
            return;
        }

        if (esHumano(evento.incident_type())) {
            procesarIncidenteHumano(evento);
            return;
        }

        log.info("Incidente {} ignorado por tipo no soportado: {}", evento.incident_id(), evento.incident_type());
    }

    private void procesarIncidenteMecanico(FallaMecanicaRecibidaEvent evento) {
        UUID idVehiculo = vehiculoConsultaPort.buscarIdPorPlaca(evento.vehicle_id())
                .orElseThrow(() -> new NoSuchElementException(
                        "Vehiculo no encontrado para placa: " + evento.vehicle_id()));

        Asignacion asignacion = asignacionRepository
                .buscarPorVehiculoId(idVehiculo)
                .orElseThrow(() -> new NoSuchElementException(
                        "Asignacion no encontrada para vehiculo: " + idVehiculo));

        SagaRegistro saga = sagaRepository.buscarPorAsignacionId(asignacion.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Saga no encontrada para asignacion: " + asignacion.getId()));

        asignacion.liberarVehiculo();
        asignacionRepository.guardar(asignacion);

        saga.marcarPendienteLiberacion();
        sagaRepository.guardar(saga);

        eventPublisher.publicar(topicVehiculosLiberar, new VehiculoLiberadoEvent(
                saga.getId(),
                idVehiculo
        ));

        eventPublisher.publicar(topicVehiculosSolicitar, new VehiculoSolicitadoEvent(
                saga.getId(),
                asignacion.getId(),
                asignacion.getTipoVehiculo(),
                asignacion.getFechaInicio(),
                asignacion.getFechaFin(),
                asignacion.getKilometros()
        ));

        log.info("Coreografia: incidente mecanico grave {}. Vehiculo {} (placa {}) liberado y re-solicitado para asignacion {}",
                evento.incident_id(), idVehiculo, evento.vehicle_id(), asignacion.getId());
    }

    private void procesarIncidenteHumano(FallaMecanicaRecibidaEvent evento) {
        Asignacion asignacion = asignacionRepository
                .buscarPorConductorId(evento.driver_id())
                .orElseThrow(() -> new NoSuchElementException(
                        "Asignacion no encontrada para conductor: " + evento.driver_id()));

        Conductor conductorActual = asignacion.getConductor();
        conductorActual.liberar();
        conductorRepository.guardar(conductorActual);

        Conductor nuevoConductor = conductorRepository
                .buscarDisponiblePorTipoVehiculo(asignacion.getTipoVehiculo())
                .orElseThrow(() -> new NoSuchElementException(
                        "No hay conductor disponible para reasignar tipo: " + asignacion.getTipoVehiculo()));

        nuevoConductor.reservar();
        conductorRepository.guardar(nuevoConductor);

        asignacion.reasignarConductor(nuevoConductor);
        asignacionRepository.guardar(asignacion);

        log.info("Coreografia: incidente humano grave {}. Conductor {} reemplazado por {} en asignacion {}",
                evento.incident_id(), conductorActual.getId(), nuevoConductor.getId(), asignacion.getId());
    }

    private boolean esGrave(String severidad) {
        return Objects.nonNull(severidad) && "GRAVE".equalsIgnoreCase(severidad.trim());
    }

    private boolean esMecanico(String tipoIncidente) {
        return Objects.nonNull(tipoIncidente) && "MECANICO".equalsIgnoreCase(tipoIncidente.trim());
    }

    private boolean esHumano(String tipoIncidente) {
        return Objects.nonNull(tipoIncidente) && "HUMANO".equalsIgnoreCase(tipoIncidente.trim());
    }
}
