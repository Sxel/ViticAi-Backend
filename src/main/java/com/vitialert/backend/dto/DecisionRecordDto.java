package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record DecisionRecordDto(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("decision_local") Boolean decisionLocal,
        @JsonProperty("decision_backend") Boolean decisionBackend,
        @JsonProperty("decision_final") boolean decisionFinal,
        @JsonProperty("accion") String accion,
        @JsonProperty("motivo") String motivo,
        @JsonProperty("source") String source,
        @JsonProperty("model_version") String modelVersion
) {
}
