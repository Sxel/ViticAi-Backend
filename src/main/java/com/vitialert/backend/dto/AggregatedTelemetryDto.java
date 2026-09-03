package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fila de la capa de agregacion temporal. El bucket se identifica por su instante de
 * inicio; el numero de muestras que lo componen se expone para que Ciencia de Datos
 * pueda descartar buckets con poca cobertura.
 */
public record AggregatedTelemetryDto(

        @JsonProperty("bucket_start") Instant bucketStart,
        @JsonProperty("sample_count") int sampleCount,

        @JsonProperty("temp_mean") Double tempMean,
        @JsonProperty("temp_max") Double tempMax,
        @JsonProperty("humidity_mean") Double humidityMean,

        @JsonProperty("soil_moisture_mean") Double soilMoistureMean,
        @JsonProperty("soil_moisture_min") Double soilMoistureMin,
        @JsonProperty("soil_moisture_max") Double soilMoistureMax,

        @JsonProperty("wind_mean") Double windMean,
        @JsonProperty("wind_max") Double windMax,

        @JsonProperty("flow_mean") BigDecimal flowMean,
        @JsonProperty("flow_max") BigDecimal flowMax,

        /** Suma de los incrementos positivos del contador acumulado dentro del bucket. */
        @JsonProperty("water_volume_used") BigDecimal waterVolumeUsed,

        /** Segundos con la electrovalvula abierta, integrados sobre los intervalos reales. */
        @JsonProperty("valve_open_seconds") long valveOpenSeconds
) {
}
