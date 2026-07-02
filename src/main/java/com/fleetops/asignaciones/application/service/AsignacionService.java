package com.fleetops.asignaciones.application.service;

import com.fleetops.asignaciones.application.port.in.CrearAsignacionUseCase;
import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.VehiculoSolicitadoEvent;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.domain.model.Conductor;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Implementa CrearAsignacionUseCase en el modelo coreografiado.
 *
 * Sin OutboxWorker, la publicación del evento ocurre dentro de la misma
 * transacción gracias a Kafka Transactions (producer configurado con
 * EXACTLY_ONCE). Si Kafka falla, la transacción hace rollback completo.
 *
 * Flujo coreografiado:
 *   1. Reserva conductor + crea Asignacion + crea SagaRegistro  (DB)
 *   2. Publica VehiculoSolicitadoEvent  (Kafka, misma tx)
 *   3. Vehículos escucha y responde con VehiculoAsignadoEvent o VehiculoNoDisponibleEvent
 *   4. KafkaVehiculosConsumer reacciona llamando al use case correspondiente
 */
@Service
@RequiredArgsConstructor
public class AsignacionService implements CrearAsignacionUseCase {

    private final ConductorRepositoryPort conductorRepository;
    private final AsignacionRepositoryPort asignacionRepository;
    private final SagaRepositoryPort sagaRepository;
    private final EventPublisherPort eventPublisher;

    @Value("${asignaciones.kafka.topics.vehiculos-solicitar}")
    private String topicVehiculosSolicitar;

    @Override
    @Transactional
    public CrearAsignacionUseCase.Result ejecutar(CrearAsignacionUseCase.Command command) {
        // 1. Buscar y reservar conductor
        Conductor conductor = conductorRepository
                .buscarDisponiblePorTipoVehiculo(command.tipoVehiculo())
                .orElseThrow(() -> new NoSuchElementException(
                        "No hay conductor disponible para tipo: " + command.tipoVehiculo()));

        conductor.reservar();
        conductorRepository.guardar(conductor);

        // 2. Crear asignación
        Asignacion asignacion = Asignacion.builder()
                .conductor(conductor)
                .tipoVehiculo(command.tipoVehiculo())
                .fechaInicio(command.fechaInicio())
                .fechaFin(command.fechaFin())
                .kilometros(command.kilometros())
                .build();
        asignacion = asignacionRepository.guardar(asignacion);

        // 3. Registrar SAGA con estado inicial
        SagaRegistro saga = SagaRegistro.builder()
                .asignacion(asignacion)
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();
        saga = sagaRepository.guardar(saga);

        // 4. Publicar evento — Vehículos reacciona (coreografía)
        //    Kafka Transactions garantiza atomicidad con el commit de DB
        eventPublisher.publicar(topicVehiculosSolicitar, new VehiculoSolicitadoEvent(
                saga.getId(),
                asignacion.getId(),
                asignacion.getTipoVehiculo(),
                asignacion.getFechaInicio(),
                asignacion.getFechaFin(),
                asignacion.getKilometros()
        ));

        return new CrearAsignacionUseCase.Result(saga.getId(), asignacion.getId());
    }
}
