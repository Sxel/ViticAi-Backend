package com.vitialert.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record NodeDto(
        @JsonProperty("id") Long id,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("nombre") String nombre,
        @JsonProperty("descripcion") String descripcion,
        @JsonProperty("activo") boolean activo,
        @JsonProperty("finca") String finca,
        @JsonProperty("sector") String sector,
        @JsonProperty("latitud") Double latitud,
        @JsonProperty("longitud") Double longitud,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
}
