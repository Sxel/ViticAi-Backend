package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de VitiAI (viti-alert-ds-api v3.0):
 * {@code GET /api/v1/satellite/features?lat=&lon=&buffer_km=}
 *
 * <p>El servicio se consulta por COORDENADAS, no por nodo: el backend resuelve la latitud y
 * la longitud a partir de la entidad {@code Node} antes de llamar.</p>
 *
 * <p>Los campos desconocidos se ignoran, de modo que VitiAI puede agregar variables sin
 * romper el backend. Para incorporar una variable nueva alcanza con agregar su componente
 * al record correspondiente.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SatelliteFeaturesDto(
        @JsonProperty("goes") Goes goes,
        @JsonProperty("sentinel") Sentinel sentinel,
        @JsonProperty("overall_quality") String overallQuality
) {

    /** Variables derivadas de nubes (GOES). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goes(
            @JsonProperty("cloud_top_temperature_c") Double cloudTopTemperatureC,
            @JsonProperty("cloud_fraction") Double cloudFraction
    ) {
    }

    /** Indices de vegetacion y humedad (Sentinel). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sentinel(
            @JsonProperty("ndvi_mean") Double ndviMean,
            @JsonProperty("ndmi_mean") Double ndmiMean
    ) {
    }
}
