package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Registro diario tal como lo produce hoy el Data Miner Python. Los nombres coinciden con
 * las columnas del dataset historico para que el CSV y el eventual endpoint REST compartan
 * el mismo contrato.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeatherDailyDto(
        @JsonProperty("fecha") LocalDate fecha,
        @JsonProperty("temp_max_c") Double tempMaxC,
        @JsonProperty("temp_media_c") Double tempMediaC,
        @JsonProperty("humedad_media_pct") Double humedadMediaPct,
        @JsonProperty("humedad_min_pct") Double humedadMinPct,
        @JsonProperty("velocidad_viento_media_kmh") Double velocidadVientoMediaKmh,
        @JsonProperty("velocidad_viento_max_kmh") Double velocidadVientoMaxKmh,
        @JsonProperty("precipitacion_mm") Double precipitacionMm,
        @JsonProperty("radiacion_solar_mj_m2") Double radiacionSolarMjM2,
        @JsonProperty("et0_mm") Double et0Mm,
        @JsonProperty("vpd_max_kpa") Double vpdMaxKpa
) {
}
