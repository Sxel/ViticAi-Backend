package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Integracion con el Data Miner meteorologico.
 *
 * <p>La via soportada hoy es la importacion de CSV procesado
 * ({@code POST /api/admin/weather/import}). {@code enabled} habilita ademas el pull
 * REST opcional cuando el Data Miner exponga un endpoint.</p>
 *
 * @param preferredSource origen preferido cuando una misma fecha tiene varias fuentes cargadas
 */
@ConfigurationProperties(prefix = "vitialert.weather")
public record WeatherProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8001") String baseUrl,
        @DefaultValue("/api/weather/daily") String dailyPath,
        @DefaultValue("2000") int connectTimeoutMs,
        @DefaultValue("5000") int readTimeoutMs,
        @DefaultValue("OPEN_METEO_ERA5") String defaultSource,
        @DefaultValue("OPEN_METEO_ERA5") String preferredSource
) {
}
