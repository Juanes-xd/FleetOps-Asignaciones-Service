package com.fleetops.asignaciones.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fleetops.asignaciones.application.port.in.CrearAsignacionUseCase;
import com.fleetops.asignaciones.infrastructure.web.dto.CrearAsignacionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsignacionController")
class AsignacionControllerTest {

    @Mock CrearAsignacionUseCase crearAsignacionUseCase;
    @InjectMocks AsignacionController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /asignaciones: dado request válido, retorna 202 Accepted con idSaga")
    void crear_dadoRequestValido_retorna202ConIdSaga() throws Exception {
        // Arrange
        UUID idSaga       = UUID.randomUUID();
        UUID idAsignacion = UUID.randomUUID();

        when(crearAsignacionUseCase.ejecutar(any()))
                .thenReturn(new CrearAsignacionUseCase.Result(idSaga, idAsignacion));

        CrearAsignacionRequest request = new CrearAsignacionRequest(
                "CAMION",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(5),
                500
        );
        // Act & Assert
        mockMvc.perform(post("/asignaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idSaga").value(idSaga.toString()))
                .andExpect(jsonPath("$.idAsignacion").value(idAsignacion.toString()))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }

        @Test
        @DisplayName("POST /asignaciones: dado tipo de vehículo vacío, retorna 400 Bad Request")
        void crear_dadoTipoVehiculoVacio_retorna400() throws Exception {
        // Arrange
        CrearAsignacionRequest request = new CrearAsignacionRequest(
                                "",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(5),
                200
        );

        // Act & Assert
        mockMvc.perform(post("/asignaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
