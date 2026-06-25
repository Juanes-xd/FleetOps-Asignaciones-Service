
package com.fleetops.asignaciones.infrastructure.web.controller;

import com.fleetops.asignaciones.application.port.in.ConsultarSagaUseCase;
import com.fleetops.asignaciones.domain.enums.EstadoSaga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaController")
class SagaControllerTest {

    @Mock
    private ConsultarSagaUseCase consultarSagaUseCase;

    @InjectMocks
    private SagaController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /asignaciones/saga/{id}: retorna 200 y estado de la saga")
    void consultar_retorna200() throws Exception {

        UUID idSaga = UUID.randomUUID();
        UUID vehiculoId = UUID.randomUUID();

        when(consultarSagaUseCase.consultar(idSaga))
                .thenReturn(new ConsultarSagaUseCase.Result(
                        idSaga,
                        EstadoSaga.COMPLETADO,
                        null,
                        vehiculoId
                ));

        mockMvc.perform(get("/asignaciones/saga/{idSaga}", idSaga))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSaga").value(idSaga.toString()))
                .andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.vehiculoId").value(vehiculoId.toString()));
    }

}
