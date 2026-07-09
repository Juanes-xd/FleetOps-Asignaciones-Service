package com.fleetops.asignaciones.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fleetops.asignaciones.application.port.in.ConsultarSagaUseCase;
import com.fleetops.asignaciones.application.port.in.CrearAsignacionUseCase;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import com.fleetops.asignaciones.infrastructure.web.controller.AsignacionController;
import com.fleetops.asignaciones.infrastructure.web.controller.SagaController;
import com.fleetops.asignaciones.infrastructure.web.dto.CrearAsignacionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Comportamiento API de Asignaciones")
class AsignacionesApiBehaviorTest {

    @Mock
    private CrearAsignacionUseCase crearAsignacionUseCase;

    @Mock
    private ConsultarSagaUseCase consultarSagaUseCase;

    private MockMvc asignacionMockMvc;
    private MockMvc sagaMockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        asignacionMockMvc = MockMvcBuilders
                .standaloneSetup(new AsignacionController(crearAsignacionUseCase))
                .build();
        sagaMockMvc = MockMvcBuilders
                .standaloneSetup(new SagaController(consultarSagaUseCase))
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /asignaciones valido devuelve 202 Accepted")
    void postAsignaciones_valido_devuelveAccepted() throws Exception {
        UUID idSaga = UUID.randomUUID();
        UUID idAsignacion = UUID.randomUUID();
        CrearAsignacionRequest request = new CrearAsignacionRequest(
                "CAMION",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(4),
                450);
        when(crearAsignacionUseCase.ejecutar(any()))
                .thenReturn(new CrearAsignacionUseCase.Result(idSaga, idAsignacion));

        asignacionMockMvc.perform(post("/asignaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idSaga").value(idSaga.toString()))
                .andExpect(jsonPath("$.idAsignacion").value(idAsignacion.toString()))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        ArgumentCaptor<CrearAsignacionUseCase.Command> commandCaptor =
                ArgumentCaptor.forClass(CrearAsignacionUseCase.Command.class);
        verify(crearAsignacionUseCase).ejecutar(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tipoVehiculo()).isEqualTo("CAMION");
        assertThat(commandCaptor.getValue().kilometros()).isEqualTo(450);
    }

    @Test
    @DisplayName("POST /asignaciones invalido devuelve 400 Bad Request")
    void postAsignaciones_invalido_devuelveBadRequest() throws Exception {
        CrearAsignacionRequest request = new CrearAsignacionRequest(
                "",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(4),
                450);

        asignacionMockMvc.perform(post("/asignaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(crearAsignacionUseCase);
    }

    @Test
    @DisplayName("GET /asignaciones/saga/{id} existente devuelve 200")
    void getSaga_existente_devuelveOk() throws Exception {
        UUID idSaga = UUID.randomUUID();
        UUID idVehiculo = UUID.randomUUID();
        when(consultarSagaUseCase.consultar(idSaga))
                .thenReturn(new ConsultarSagaUseCase.Result(
                        idSaga,
                        EstadoSaga.COMPLETADO,
                        null,
                        idVehiculo));

        sagaMockMvc.perform(get("/asignaciones/saga/{idSaga}", idSaga))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSaga").value(idSaga.toString()))
                .andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.vehiculoId").value(idVehiculo.toString()))
                .andExpect(jsonPath("$.motivoFallo").doesNotExist());

        verify(consultarSagaUseCase).consultar(idSaga);
    }

    @Test
    @DisplayName("GET /asignaciones/saga/{id} inexistente propaga el error real del proyecto")
    void getSaga_inexistente_propagaErrorReal() {
        UUID idSaga = UUID.randomUUID();
        when(consultarSagaUseCase.consultar(idSaga))
                .thenThrow(new NoSuchElementException("Saga no encontrada: " + idSaga));

        assertThatThrownBy(() -> sagaMockMvc.perform(get("/asignaciones/saga/{idSaga}", idSaga)))
                .hasRootCauseInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Saga no encontrada: " + idSaga);

        verify(consultarSagaUseCase).consultar(idSaga);
    }
}
