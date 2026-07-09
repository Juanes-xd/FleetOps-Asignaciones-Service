package com.fleetops.asignaciones.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoAsignadoUseCase;
import com.fleetops.asignaciones.application.port.in.ProcesarVehiculoRechazadoUseCase;
import com.fleetops.asignaciones.application.port.out.AsignacionRepositoryPort;
import com.fleetops.asignaciones.application.port.out.ConductorRepositoryPort;
import com.fleetops.asignaciones.application.port.out.EventPublisherPort;
import com.fleetops.asignaciones.application.port.out.SagaRepositoryPort;
import com.fleetops.asignaciones.application.port.out.VehiculoConsultaPort;
import com.fleetops.asignaciones.application.service.ReasignacionService;
import com.fleetops.asignaciones.application.service.VehiculoAsignadoService;
import com.fleetops.asignaciones.application.service.VehiculoRechazadoService;
import com.fleetops.asignaciones.domain.enums.EstadoConductor;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.domain.event.AsignacionCompletadaEvent;
import com.fleetops.asignaciones.domain.event.AsignacionFallidaEvent;
import com.fleetops.asignaciones.domain.event.VehiculoLiberadoEvent;
import com.fleetops.asignaciones.domain.event.VehiculoSolicitadoEvent;
import com.fleetops.asignaciones.domain.model.Asignacion;
import com.fleetops.asignaciones.domain.model.Conductor;
import com.fleetops.asignaciones.domain.model.SagaRegistro;
import com.fleetops.asignaciones.infrastructure.messaging.consumer.KafkaVehiculosConsumer;
import com.fleetops.asignaciones.infrastructure.messaging.consumer.SqsIncidentesConsumer;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Comportamiento ante eventos externos")
class AsignacionesExternalEventsBehaviorTest {

    private static final String TOPIC_ASIGNACION_COMPLETADA = "fleetops.asignaciones.completada";
    private static final String TOPIC_ASIGNACION_FALLIDA = "fleetops.asignaciones.fallida";
    private static final String TOPIC_VEHICULOS_LIBERAR = "fleetops.vehiculos.liberar";
    private static final String TOPIC_VEHICULOS_SOLICITAR = "fleetops.vehiculos.solicitar";

    @Mock
    private AsignacionRepositoryPort asignacionRepository;

    @Mock
    private ConductorRepositoryPort conductorRepository;

    @Mock
    private SagaRepositoryPort sagaRepository;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private VehiculoConsultaPort vehiculoConsultaPort;

    @Mock
    private Acknowledgment kafkaAck;

    @Mock
    private Acknowledgement sqsAck;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Vehiculos confirma: completa asignacion, saga, publica evento y hace ACK")
    void vehiculosConfirma_desdeKafka_completaAsignacionSagaPublicaYHaceAck() {
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();
        Conductor conductor = Conductor.builder()
                .id(UUID.randomUUID())
                .estado(EstadoConductor.RESERVADO)
                .build();
        Asignacion asignacion = Asignacion.builder()
                .id(idAsignacion)
                .conductor(conductor)
                .build();
        SagaRegistro saga = SagaRegistro.builder()
                .id(UUID.randomUUID())
                .asignacion(asignacion)
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.of(asignacion));
        when(sagaRepository.buscarPorAsignacionId(idAsignacion)).thenReturn(Optional.of(saga));
        when(asignacionRepository.guardar(any(Asignacion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sagaRepository.guardar(any(SagaRegistro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KafkaVehiculosConsumer consumer = new KafkaVehiculosConsumer(
                vehiculoAsignadoService(),
                mock(ProcesarVehiculoRechazadoUseCase.class));

        consumer.onVehiculoConfirmado(Map.of(
                "idAsignacion", idAsignacion.toString(),
                "idVehiculo", idVehiculo.toString()
        ), kafkaAck);

        assertThat(asignacion.getVehiculoId()).isEqualTo(idVehiculo);
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.COMPLETADO);
        assertThat(saga.getVehiculoId()).isEqualTo(idVehiculo);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq(TOPIC_ASIGNACION_COMPLETADA), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(AsignacionCompletadaEvent.class);

        AsignacionCompletadaEvent event = (AsignacionCompletadaEvent) eventCaptor.getValue();
        assertThat(event.idSaga()).isEqualTo(saga.getId());
        assertThat(event.idAsignacion()).isEqualTo(idAsignacion);
        assertThat(event.idVehiculo()).isEqualTo(idVehiculo);
        assertThat(event.idConductor()).isEqualTo(conductor.getId());
        verify(kafkaAck).acknowledge();
    }

    @Test
    @DisplayName("Vehiculos rechaza: libera conductor, falla saga, publica evento y hace ACK")
    void vehiculosRechaza_desdeKafka_liberaConductorMarcaFallidaPublicaYHaceAck() {
        UUID idAsignacion = UUID.randomUUID();
        String motivo = "Sin vehiculos disponibles";
        Conductor conductor = Conductor.builder()
                .id(UUID.randomUUID())
                .estado(EstadoConductor.RESERVADO)
                .build();
        Asignacion asignacion = Asignacion.builder()
                .id(idAsignacion)
                .conductor(conductor)
                .build();
        SagaRegistro saga = SagaRegistro.builder()
                .id(UUID.randomUUID())
                .asignacion(asignacion)
                .estado(EstadoSaga.PENDIENTE_VEHICULO)
                .build();

        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.of(asignacion));
        when(sagaRepository.buscarPorAsignacionId(idAsignacion)).thenReturn(Optional.of(saga));
        when(conductorRepository.guardar(any(Conductor.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sagaRepository.guardar(any(SagaRegistro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KafkaVehiculosConsumer consumer = new KafkaVehiculosConsumer(
                mock(ProcesarVehiculoAsignadoUseCase.class),
                vehiculoRechazadoService());

        consumer.onVehiculoRechazado(Map.of(
                "idAsignacion", idAsignacion.toString(),
                "motivo", motivo
        ), kafkaAck);

        assertThat(conductor.getEstado()).isEqualTo(EstadoConductor.DISPONIBLE);
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.FALLIDO);
        assertThat(saga.getMotivoFallo()).isEqualTo(motivo);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq(TOPIC_ASIGNACION_FALLIDA), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(AsignacionFallidaEvent.class);

        AsignacionFallidaEvent event = (AsignacionFallidaEvent) eventCaptor.getValue();
        assertThat(event.idSaga()).isEqualTo(saga.getId());
        assertThat(event.idAsignacion()).isEqualTo(idAsignacion);
        assertThat(event.motivo()).isEqualTo(motivo);
        verify(kafkaAck).acknowledge();
    }

    @Test
    @DisplayName("Vehiculos confirma asignacion inexistente: el servicio lanza y no publica eventos")
    void vehiculosConfirmaAsignacionInexistente_servicioLanzaYNoPublicaEventos() {
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();
        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehiculoAsignadoService().procesar(idAsignacion, idVehiculo))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Asignacion no encontrada")
                .hasMessageContaining(idAsignacion.toString());

        verifyNoInteractions(sagaRepository, eventPublisher);
    }

    @Test
    @DisplayName("Vehiculos confirma asignacion inexistente desde Kafka: no publica ni hace ACK")
    void vehiculosConfirmaAsignacionInexistente_desdeKafkaNoPublicaNiHaceAck() {
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();
        when(asignacionRepository.buscarPorId(idAsignacion)).thenReturn(Optional.empty());

        KafkaVehiculosConsumer consumer = new KafkaVehiculosConsumer(
                vehiculoAsignadoService(),
                mock(ProcesarVehiculoRechazadoUseCase.class));

        consumer.onVehiculoConfirmado(Map.of(
                "idAsignacion", idAsignacion.toString(),
                "idVehiculo", idVehiculo.toString()
        ), kafkaAck);

        verify(eventPublisher, never()).publicar(any(), any());
        verify(kafkaAck, never()).acknowledge();
        verifyNoInteractions(sagaRepository, conductorRepository);
    }

    @Test
    @DisplayName("Incidentes reporta falla mecanica grave: libera vehiculo y publica compensacion")
    void incidenteMecanicoGrave_desdeSqs_liberaVehiculoYPublicaCompensacion() throws Exception {
        UUID idAsignacion = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();
        UUID idConductor = UUID.randomUUID();
        Asignacion asignacion = Asignacion.builder()
                .id(idAsignacion)
                .vehiculoId(idVehiculo)
                .tipoVehiculo("CAMION")
                .fechaInicio(LocalDate.now().plusDays(1))
                .fechaFin(LocalDate.now().plusDays(3))
                .kilometros(250)
                .build();
        SagaRegistro saga = SagaRegistro.builder()
                .id(UUID.randomUUID())
                .asignacion(asignacion)
                .estado(EstadoSaga.COMPLETADO)
                .build();

        when(vehiculoConsultaPort.buscarIdPorPlaca(idVehiculo.toString())).thenReturn(Optional.of(idVehiculo));
        when(asignacionRepository.buscarPorVehiculoId(idVehiculo)).thenReturn(Optional.of(asignacion));
        when(sagaRepository.buscarPorAsignacionId(idAsignacion)).thenReturn(Optional.of(saga));
        when(asignacionRepository.guardar(any(Asignacion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sagaRepository.guardar(any(SagaRegistro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SqsIncidentesConsumer consumer = new SqsIncidentesConsumer(reasignacionService(), objectMapper);

        consumer.onFallaMecanica(
                snsEnvelope(fallaMecanicaMessage(idVehiculo, idConductor, "MECANICO", "GRAVE")),
                sqsAck);

        assertThat(asignacion.getVehiculoId()).isNull();
        assertThat(saga.getEstado()).isEqualTo(EstadoSaga.PENDIENTE_LIBERACION);

        ArgumentCaptor<Object> liberarCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq(TOPIC_VEHICULOS_LIBERAR), liberarCaptor.capture());
        assertThat(liberarCaptor.getValue()).isInstanceOf(VehiculoLiberadoEvent.class);
        assertThat(((VehiculoLiberadoEvent) liberarCaptor.getValue()).idVehiculo()).isEqualTo(idVehiculo);

        ArgumentCaptor<Object> solicitarCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publicar(eq(TOPIC_VEHICULOS_SOLICITAR), solicitarCaptor.capture());
        assertThat(solicitarCaptor.getValue()).isInstanceOf(VehiculoSolicitadoEvent.class);
        verify(sqsAck).acknowledge();
    }

    @Test
    @DisplayName("Incidentes reporta incidente leve: no modifica ni publica eventos")
    void incidenteLeve_desdeSqs_noModificaNiPublicaEventos() throws Exception {
        UUID idVehiculo = UUID.randomUUID();
        UUID idConductor = UUID.randomUUID();
        SqsIncidentesConsumer consumer = new SqsIncidentesConsumer(reasignacionService(), objectMapper);

        consumer.onFallaMecanica(
                snsEnvelope(fallaMecanicaMessage(idVehiculo, idConductor, "MECANICO", "LEVE")),
                sqsAck);

        verifyNoInteractions(asignacionRepository, conductorRepository, sagaRepository, eventPublisher, vehiculoConsultaPort);
        verify(sqsAck).acknowledge();
    }

    private VehiculoAsignadoService vehiculoAsignadoService() {
        VehiculoAsignadoService service = new VehiculoAsignadoService(
                asignacionRepository,
                sagaRepository,
                eventPublisher);
        ReflectionTestUtils.setField(service, "topicAsignacionCompletada", TOPIC_ASIGNACION_COMPLETADA);
        return service;
    }

    private VehiculoRechazadoService vehiculoRechazadoService() {
        VehiculoRechazadoService service = new VehiculoRechazadoService(
                asignacionRepository,
                conductorRepository,
                sagaRepository,
                eventPublisher);
        ReflectionTestUtils.setField(service, "topicAsignacionFallida", TOPIC_ASIGNACION_FALLIDA);
        return service;
    }

    private ReasignacionService reasignacionService() {
        ReasignacionService service = new ReasignacionService(
                asignacionRepository,
                conductorRepository,
                sagaRepository,
                eventPublisher,
                vehiculoConsultaPort);
        ReflectionTestUtils.setField(service, "topicVehiculosLiberar", TOPIC_VEHICULOS_LIBERAR);
        ReflectionTestUtils.setField(service, "topicVehiculosSolicitar", TOPIC_VEHICULOS_SOLICITAR);
        return service;
    }

    private String fallaMecanicaMessage(UUID idVehiculo, UUID idConductor, String tipo, String severidad)
            throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("incident_id", UUID.randomUUID().toString());
        message.put("vehicle_id", idVehiculo.toString());
        message.put("description", "Evento externo de prueba");
        message.put("driver_id", idConductor.toString());
        message.put("incident_type", tipo);
        message.put("severity", severidad);
        message.put("event_date", "2026-07-02T10:15:00Z");
        return objectMapper.writeValueAsString(message);
    }

    private String snsEnvelope(String innerMessage) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("Type", "Notification");
        envelope.put("MessageId", UUID.randomUUID().toString());
        envelope.put("TopicArn", "arn:aws:sns:us-east-1:123456789012:fleetops-incidentes-falla-mecanica");
        envelope.put("Message", innerMessage);
        envelope.put("Timestamp", "2026-07-02T10:15:01.000Z");
        return objectMapper.writeValueAsString(envelope);
    }
}
