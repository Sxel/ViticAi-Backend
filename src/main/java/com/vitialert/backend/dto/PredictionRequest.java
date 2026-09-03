package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Peticion enviada al servicio Python de inferencia. Las features viajan como mapa
 * nombre -&gt; valor para que agregar una variable nueva no obligue a recompilar Java.
 * Un valor faltante viaja como {@code null}, no como cero.
 */
public record PredictionRequest(
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("features") Map<String, Double> features
) {
}
