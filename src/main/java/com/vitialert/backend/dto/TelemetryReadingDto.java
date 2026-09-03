package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record TelemetryReadingDto(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("timestamp_received") Instant timestampReceived,
        @JsonProperty("temperatura_ambiente_c") Double temperaturaAmbienteC,
        @JsonProperty("humedad_relativa_pct") Double humedadRelativaPct,
        @JsonProperty("humedad_suelo_pct") Double humedadSueloPct,
        @JsonProperty("humedad_suelo_raw") Integer humedadSueloRaw,
        @JsonProperty("velocidad_viento_kmh") Double velocidadVientoKmh,
        @JsonProperty("caudal_l_min") BigDecimal caudalLMin,
        @JsonProperty("volumen_total_l") BigDecimal volumenTotalL,
        @JsonProperty("valvula_abierta_actual") Boolean valvulaAbiertaActual,
        @JsonProperty("decision_riego_local") Boolean decisionRiegoLocal,
        @JsonProperty("quality_flag") String qualityFlag,
        @JsonProperty("quality_notes") String qualityNotes
) {
}
