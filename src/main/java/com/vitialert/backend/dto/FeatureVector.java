package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Features temporales derivadas EXCLUSIVAMENTE de la telemetria IoT.
 *
 * <p>Todos los campos son nullables a proposito. Cuando no existe una observacion
 * cercana al instante buscado el valor es {@code null} (dato faltante) y NUNCA cero:
 * un cero artificial seria indistinguible de "suelo completamente seco" y arruinaria
 * el entrenamiento del modelo.</p>
 *
 * <p>Las variables meteorologicas (lluvia, ET0, VPD, radiacion) NO se calculan aca:
 * pertenecen a otra fuente ({@code WeatherObservation}) y se integran en el dataset
 * exportado.</p>
 */
public record FeatureVector(

        @JsonProperty("node_id") String nodeId,
        @JsonProperty("timestamp") Instant timestamp,

        @JsonProperty("soil_moisture_pct") Double soilMoisturePct,
        @JsonProperty("soil_moisture_lag_1h") Double soilMoistureLag1h,
        @JsonProperty("soil_moisture_lag_3h") Double soilMoistureLag3h,
        @JsonProperty("soil_moisture_lag_6h") Double soilMoistureLag6h,
        @JsonProperty("soil_moisture_lag_24h") Double soilMoistureLag24h,

        /** Pendiente en puntos porcentuales por hora entre t-3h y t. */
        @JsonProperty("soil_moisture_slope_3h") Double soilMoistureSlope3h,

        /** Pendiente en puntos porcentuales por hora entre t-12h y t. */
        @JsonProperty("soil_moisture_slope_12h") Double soilMoistureSlope12h,

        @JsonProperty("soil_moisture_mean_24h") Double soilMoistureMean24h,

        @JsonProperty("temperature_c") Double temperatureC,
        @JsonProperty("relative_humidity_pct") Double relativeHumidityPct,
        @JsonProperty("wind_speed_kmh") Double windSpeedKmh,
        @JsonProperty("flow_l_min") BigDecimal flowLMin,

        @JsonProperty("irrigation_volume_1h") BigDecimal irrigationVolume1h,
        @JsonProperty("irrigation_volume_24h") BigDecimal irrigationVolume24h
) {
}
