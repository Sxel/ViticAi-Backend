package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Contrato de lectura del servicio Python VitiAlert satelital (GOES / Sentinel).
 *
 * <p>El backend Java NO calcula nada satelital: solamente consume este DTO cuando el
 * servicio esta disponible. Campos desconocidos se ignoran para que el servicio Python
 * pueda evolucionar sin romper el backend.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SatelliteObservationDto(

        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("cloud_fraction") Double cloudFraction,
        @JsonProperty("cloud_top_temperature_c") Double cloudTopTemperatureC,
        @JsonProperty("cloud_top_height_m") Double cloudTopHeightM,
        @JsonProperty("cloud_phase") String cloudPhase,
        @JsonProperty("rainfall_rate_mm_h") Double rainfallRateMmH,
        @JsonProperty("ndvi") Double ndvi,
        @JsonProperty("ndmi") Double ndmi,
        @JsonProperty("image_date") LocalDate imageDate,
        @JsonProperty("data_quality") String dataQuality
) {
}
