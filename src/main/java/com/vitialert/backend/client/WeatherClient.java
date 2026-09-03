package com.vitialert.backend.client;

import com.vitialert.backend.config.WeatherProperties;
import com.vitialert.backend.dto.WeatherDailyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Integracion opcional (pull REST) con el Data Miner meteorologico Python.
 *
 * <p>El camino soportado y probado hoy es la importacion de CSV procesado por
 * {@code POST /api/admin/weather/import}. Este cliente queda preparado para el dia en que
 * el Data Miner exponga un endpoint HTTP; el Data Miner NO se porta a Java.</p>
 */
@Component
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);

    private final RestClient restClient;
    private final WeatherProperties properties;

    public WeatherClient(@Qualifier("weatherRestClient") RestClient restClient,
                         WeatherProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * @return vacio si la integracion esta desactivada o el Data Miner no responde
     */
    public Optional<List<WeatherDailyDto>> fetchDaily(LocalDate from, LocalDate to) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            List<WeatherDailyDto> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(properties.dailyPath())
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<WeatherDailyDto>>() {
                    });
            return Optional.ofNullable(response);
        } catch (RuntimeException ex) {
            log.warn("Data Miner meteorologico no disponible ({} a {}): {}", from, to, ex.getMessage());
            return Optional.empty();
        }
    }
}
