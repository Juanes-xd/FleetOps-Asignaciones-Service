package com.fleetops.asignaciones.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fleetops.asignaciones.application.port.out.VehiculoConsultaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador saliente hacia el microservicio de Vehículos: resuelve la placa reportada por
 * Incidentes al idVehiculo interno vía GET /vehiculos/placa/{placa}.
 *
 * Ese endpoint exige el mismo JWT RS256 que protege POST /asignaciones (mismo emisor,
 * misma llave pública). Este microservicio nunca firma tokens — solo los valida (ver
 * JwtDecoderConfig) — así que no genera uno nuevo para esta llamada: reenvía el mismo
 * JWT que {@link com.fleetops.asignaciones.infrastructure.security.JwtAuthenticationFilter}
 * ya validó y dejó en el SecurityContext de la petición HTTP en curso.
 *
 * Esto solo funciona cuando hay una petición HTTP viva en el hilo actual. La reacción a
 * fallas mecánicas (SqsIncidentesConsumer -> ReasignacionService) corre fuera de cualquier
 * request HTTP, así que en ese flujo no hay Authentication que reenviar.
 */
@Slf4j
@Component
public class VehiculoRestClientAdapter implements VehiculoConsultaPort {

    private final RestClient restClient;

    public VehiculoRestClientAdapter(RestClient.Builder restClientBuilder,
                                      @Value("${asignaciones.vehiculos.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<UUID> buscarIdPorPlaca(String placa) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/vehiculos/placa/{placa}", placa);

            String token = tokenDeLaPeticionActual();
            if (token != null) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            } else {
                log.warn("No hay un JWT autenticado en el contexto actual; la consulta de la placa {} "
                        + "se enviará sin Authorization y probablemente Vehiculos la rechace con 401", placa);
            }

            VehiculoResponse response = request.retrieve().body(VehiculoResponse.class);

            if (response == null || response.idVehiculo() == null) {
                log.warn("Servicio de Vehiculos respondió sin idVehiculo para placa {}", placa);
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(response.idVehiculo()));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.warn("No existe vehiculo con placa {} en el servicio de Vehiculos", placa);
                return Optional.empty();
            }
            throw ex;
        }
    }

    private String tokenDeLaPeticionActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwtAuthenticacion) {
            return jwtAuthenticacion.getToken().getTokenValue();
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VehiculoResponse(String idVehiculo) {}
}
