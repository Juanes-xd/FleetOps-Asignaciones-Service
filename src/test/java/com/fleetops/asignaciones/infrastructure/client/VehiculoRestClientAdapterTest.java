package com.fleetops.asignaciones.infrastructure.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("VehiculoRestClientAdapter")
class VehiculoRestClientAdapterTest {

    private static final String BASE_URL = "http://vehiculos.test";

    private MockRestServiceServer mockServer;
    private VehiculoRestClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new VehiculoRestClientAdapter(builder, BASE_URL);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("buscarIdPorPlaca: reenvia el JWT de la petición HTTP en curso como Bearer y resuelve el idVehiculo")
    void buscarIdPorPlaca_conPeticionAutenticada_reenviaElMismoJwt() {
        UUID idVehiculo = UUID.randomUUID();
        String placa = "AUT004";
        String tokenDeLaPeticionActual = "jwt-de-la-peticion-http-en-curso";

        Jwt jwt = Jwt.withTokenValue(tokenDeLaPeticionActual)
                .header("alg", "RS256")
                .subject("usuario-test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        mockServer.expect(requestTo(BASE_URL + "/vehiculos/placa/" + placa))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeLaPeticionActual))
                .andRespond(withSuccess(
                        "{\"idVehiculo\":\"" + idVehiculo + "\",\"numeroPlaca\":\"" + placa + "\"}",
                        MediaType.APPLICATION_JSON));

        Optional<UUID> resultado = adapter.buscarIdPorPlaca(placa);

        assertThat(resultado).contains(idVehiculo);
        mockServer.verify();
    }

    @Test
    @DisplayName("buscarIdPorPlaca: sin JWT en el contexto de seguridad, llama sin header Authorization")
    void buscarIdPorPlaca_sinAutenticacion_llamaSinAuthorization() {
        UUID idVehiculo = UUID.randomUUID();
        String placa = "AUT004";

        mockServer.expect(requestTo(BASE_URL + "/vehiculos/placa/" + placa))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(
                        "{\"idVehiculo\":\"" + idVehiculo + "\"}",
                        MediaType.APPLICATION_JSON));

        Optional<UUID> resultado = adapter.buscarIdPorPlaca(placa);

        assertThat(resultado).contains(idVehiculo);
        mockServer.verify();
    }

    @Test
    @DisplayName("buscarIdPorPlaca: con una Authentication que no es JWT, llama sin header Authorization")
    void buscarIdPorPlaca_conAutenticacionNoJwt_llamaSinAuthorization() {
        UUID idVehiculo = UUID.randomUUID();
        String placa = "AUT004";
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("usuario-test", "N/A"));

        mockServer.expect(requestTo(BASE_URL + "/vehiculos/placa/" + placa))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(
                        "{\"idVehiculo\":\"" + idVehiculo + "\"}",
                        MediaType.APPLICATION_JSON));

        Optional<UUID> resultado = adapter.buscarIdPorPlaca(placa);

        assertThat(resultado).contains(idVehiculo);
        mockServer.verify();
    }

    @Test
    @DisplayName("buscarIdPorPlaca: dada placa inexistente (404), retorna Optional vacio")
    void buscarIdPorPlaca_dadaPlacaInexistente_retornaVacio() {
        String placa = "ZZZ999";

        mockServer.expect(requestTo(BASE_URL + "/vehiculos/placa/" + placa))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<UUID> resultado = adapter.buscarIdPorPlaca(placa);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("buscarIdPorPlaca: dado error del servidor de Vehiculos, propaga la excepcion")
    void buscarIdPorPlaca_dadoErrorDelServidor_propagaExcepcion() {
        String placa = "AUT004";

        mockServer.expect(requestTo(BASE_URL + "/vehiculos/placa/" + placa))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.buscarIdPorPlaca(placa))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
