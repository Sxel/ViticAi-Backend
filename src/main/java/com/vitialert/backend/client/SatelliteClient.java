package com.vitialert.backend.client;

import com.vitialert.backend.config.SatelliteProperties;
import com.vitialert.backend.dto.SatelliteObservationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Integracion desacoplada con el servicio Python VitiAlert satelital (GOES / Sentinel).
 *
 * <p>El backend Java NO implementa logica satelital. Este cliente solo consulta.</p>
 *
 * <p><b>Regla de disponibilidad:</b> si el servicio esta caido, lento o devuelve un error,
 * el metodo devuelve {@link Optional#empty()} y registra una advertencia. Nunca lanza
 * excepciones hacia arriba, de modo que la telemetria se sigue aceptando y persistiendo y
 * la valvula sigue gobernada por el fallback local.</p>
 */
@Component
public class SatelliteClient {

    private static final Logger log = LoggerFactory.getLogger(SatelliteClient.class);

    private final RestClient restClient;
    private final SatelliteProperties properties;

    public SatelliteClient(@Qualifier("satelliteRestClient") RestClient restClient,
                           SatelliteProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Ultima observacion satelital disponible para un nodo.
     *
     * @return vacio si la integracion esta desactivada o el servicio no responde
     */
    public Optional<SatelliteObservationDto> fetchLatest(String nodeExternalId) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            SatelliteObservationDto response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(properties.latestPath())
                            .queryParam("node_id", nodeExternalId)
                            .build())
                    .retrieve()
                    .body(SatelliteObservationDto.class);
            return Optional.ofNullable(response);
        } catch (RuntimeException ex) {
            log.warn("VitiAlert satelital no disponible (nodo={}): {}", nodeExternalId, ex.getMessage());
            return Optional.empty();
        }
    }
}
