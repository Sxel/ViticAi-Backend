package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Integracion con el servicio Python VitiAlert satelital (GOES / Sentinel). Desactivada por defecto. */
@ConfigurationProperties(prefix = "vitialert.satellite")
public record SatelliteProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("/api/satellite/latest") String latestPath,
        @DefaultValue("2000") int connectTimeoutMs,
        @DefaultValue("3000") int readTimeoutMs
) {
}
